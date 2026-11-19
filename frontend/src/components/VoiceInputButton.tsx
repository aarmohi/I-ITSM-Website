import { Mic, Square } from 'lucide-react'
import { useSpeechInput } from '../lib/speech'

/**
 * A "Dictate" button for a text field. Renders nothing in browsers without speech recognition
 * (for example Firefox), so typing always remains the main way to fill the form.
 */
export function VoiceInputButton({ onPhrase, label = 'Dictate' }: { onPhrase: (text: string) => void; label?: string }) {
  const speech = useSpeechInput(onPhrase)

  if (!speech.supported) return null

  return (
    <div className="voice-input">
      <button
        type="button"
        className={`button button-ghost button-sm ${speech.listening ? 'voice-listening' : ''}`}
        onClick={speech.listening ? speech.stop : speech.start}
        aria-pressed={speech.listening}
      >
        {speech.listening ? <Square size={13} /> : <Mic size={14} />}
        {speech.listening ? 'Stop dictating' : label}
      </button>
      <span className="voice-status" aria-live="polite">
        {speech.error ? (
          <span className="voice-error">{speech.error}</span>
        ) : speech.listening ? (
          speech.interim ? <em>{speech.interim}</em> : 'Listening… speak now.'
        ) : null}
      </span>
    </div>
  )
}
