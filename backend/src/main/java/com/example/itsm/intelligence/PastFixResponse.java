package com.example.itsm.intelligence;

import java.time.Instant;

/**
 * A similar past fix shown on the report form. The fix itself (service and resolution notes) is
 * always shown, because that is what helps the reporter. As with duplicate warnings, the original
 * incident's id and title are withheld ({@code accessible = false}) when the viewer may not open it.
 */
public record PastFixResponse(
        Long incidentId,
        String reference,
        String title,
        String serviceName,
        String resolutionNotes,
        Instant resolvedAt,
        double score,
        boolean accessible
) {

    public static PastFixResponse of(PastFixMatch match, boolean accessible) {
        var incident = match.incident();
        return new PastFixResponse(
                accessible ? incident.getId() : null,
                incident.getReference(),
                accessible ? incident.getTitle() : null,
                incident.getService().getName(),
                incident.getResolutionNotes(),
                incident.getResolvedAt(),
                match.score(),
                accessible
        );
    }
}
