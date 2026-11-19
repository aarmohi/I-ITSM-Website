package com.example.itsm.intelligence;

import java.time.Instant;

/** A past fix shown to support staff on an incident's page. */
public record ResolutionSuggestion(
        Long incidentId,
        String reference,
        String title,
        String resolutionNotes,
        Instant resolvedAt,
        double score
) {

    public static ResolutionSuggestion of(PastFixMatch match) {
        var incident = match.incident();
        return new ResolutionSuggestion(
                incident.getId(),
                incident.getReference(),
                incident.getTitle(),
                incident.getResolutionNotes(),
                incident.getResolvedAt(),
                match.score()
        );
    }
}
