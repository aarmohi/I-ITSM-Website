import { useEffect, useRef, useState } from 'react'

/*
 * Voice input through the browser's built-in Web Speech API. No API key, package or backend call:
 * the browser does the recognition (Chrome uses Google's service, Edge uses Microsoft's), so it
 * needs an internet connection and is not available in Firefox. TypeScript's DOM library does not
 * describe this API, so the small part we use is typed here.
 */

interface RecognitionAlternative {
  transcript: string
}

interface RecognitionResult {
  readonly isFinal: boolean
  readonly length: number
  readonly [index: number]: RecognitionAlternative
}

interface RecognitionEvent {
  readonly resultIndex: number
  readonly results: ArrayLike<RecognitionResult>
}

interface Recognition {
  lang: string
  continuous: boolean
  interimResults: boolean
  onresult: ((event: RecognitionEvent) => void) | null
  onerror: ((event: { error: string }) => void) | null
  onend: (() => void) | null
  start(): void
  stop(): void
  abort(): void
}

type RecognitionConstructor = new () => Recognition

function recognitionConstructor(): RecognitionConstructor | null {
  if (typeof window === 'undefined') return null
  const speechWindow = window as unknown as {
    SpeechRecognition?: RecognitionConstructor
    webkitSpeechRecognition?: RecognitionConstructor
  }
  return speechWindow.SpeechRecognition ?? speechWindow.webkitSpeechRecognition ?? null
}

const ERROR_MESSAGES: Record<string, string> = {
  'not-allowed': 'Microphone access is blocked. Allow it in the browser to use voice input.',
  'service-not-allowed': 'Microphone access is blocked. Allow it in the browser to use voice input.',
  'no-speech': 'No speech was heard. Try again a little closer to the microphone.',
  'audio-capture': 'No microphone was found on this device.',
  network: 'Voice input needs an internet connection.',
}

export const speechSupported = recognitionConstructor() !== null

/**
 * Starts and stops dictation. Each finished phrase is passed to {@code onPhrase}; the phrase still
 * being spoken is exposed as {@code interim} so the user sees that they are being heard.
 */
export function useSpeechInput(onPhrase: (text: string) => void, lang = 'en-IN') {
  const [listening, setListening] = useState(false)
  const [interim, setInterim] = useState('')
  const [error, setError] = useState<string | null>(null)
  const recognitionRef = useRef<Recognition | null>(null)
  const onPhraseRef = useRef(onPhrase)

  useEffect(() => {
    onPhraseRef.current = onPhrase
  }, [onPhrase])

  // Stop listening if the page is left mid-dictation.
  useEffect(() => () => recognitionRef.current?.abort(), [])

  function start() {
    const Constructor = recognitionConstructor()
    if (!Constructor || recognitionRef.current) return
    const recognition = new Constructor()
    recognition.lang = lang
    recognition.continuous = true
    recognition.interimResults = true
    recognition.onresult = (event) => {
      let pending = ''
      for (let index = event.resultIndex; index < event.results.length; index++) {
        const result = event.results[index]
        const text = result[0]?.transcript ?? ''
        if (result.isFinal) {
          if (text.trim()) onPhraseRef.current(text.trim())
        } else {
          pending += text
        }
      }
      setInterim(pending)
    }
    recognition.onerror = (event) => {
      if (event.error !== 'aborted') {
        setError(ERROR_MESSAGES[event.error] ?? 'Voice input stopped unexpectedly. Please try again.')
      }
    }
    recognition.onend = () => {
      recognitionRef.current = null
      setListening(false)
      setInterim('')
    }
    setError(null)
    try {
      recognition.start()
      recognitionRef.current = recognition
      setListening(true)
    } catch {
      setError('Voice input could not start. Please try again.')
    }
  }

  function stop() {
    recognitionRef.current?.stop()
  }

  return { supported: speechSupported, listening, interim, error, start, stop }
}

/** Adds a dictated phrase to existing text: a space between, and a capital after a full stop. */
export function appendDictation(existing: string, phrase: string, maxLength: number): string {
  const base = existing.trimEnd()
  const startsSentence = base === '' || /[.!?]$/.test(base)
  const text = startsSentence ? phrase.charAt(0).toUpperCase() + phrase.slice(1) : phrase
  return (base ? `${base} ${text}` : text).slice(0, maxLength)
}
