package com.example.itsm.intelligence;

/** The affected service the report most likely refers to, with the words that matched. */
public record ServiceSuggestion(
        Long serviceId,
        String serviceName,
        double confidence,
        String rationale
) {
}
