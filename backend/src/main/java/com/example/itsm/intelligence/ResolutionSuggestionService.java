package com.example.itsm.intelligence;

import com.example.itsm.incident.Incident;
import com.example.itsm.incident.IncidentRepository;
import com.example.itsm.incident.IncidentStatusGroups;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Finds resolved incidents that look like a given problem, so their resolution notes can be reused:
 * technicians see them as hints on an incident, and reporters see them on the report form as
 * "similar past fixes" before they submit.
 */
@Service
public class ResolutionSuggestionService {

    /** Technicians get looser matches as hints while they work. */
    public static final double TECHNICIAN_THRESHOLD = 0.15;
    /** Reporters only see strong matches, so the form never suggests an unrelated fix. */
    public static final double REPORTER_THRESHOLD = 0.25;

    private static final int LOOKBACK_DAYS = 180;
    private static final int MAX_CANDIDATES = 500;
    private static final double SAME_SERVICE_BONUS = 0.10;
    /** Ids start at 1, so this excludes nothing when there is no incident to exclude yet. */
    private static final long NO_INCIDENT = -1L;

    private final IncidentRepository incidentRepository;
    private final Clock clock;

    public ResolutionSuggestionService(IncidentRepository incidentRepository, Clock clock) {
        this.incidentRepository = incidentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PastFixMatch> findPastFixes(
            String title,
            String description,
            Long serviceId,
            Long excludeIncidentId,
            double minScore,
            int limit
    ) {
        Instant since = clock.instant().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
        // The database keeps only resolved incidents with notes, newest first and capped;
        // only the text similarity runs in Java (same approach as duplicate detection).
        List<Incident> resolved = incidentRepository.findResolvedForSuggestions(
                IncidentStatusGroups.RESOLVED_STATUSES,
                since,
                excludeIncidentId == null ? NO_INCIDENT : excludeIncidentId,
                PageRequest.of(0, MAX_CANDIDATES)
        );
        return resolved.stream()
                .map(incident -> new PastFixMatch(incident, score(title, description, serviceId, incident)))
                .filter(match -> match.score() >= minScore)
                .sorted(Comparator.comparingDouble(PastFixMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    private double score(String title, String description, Long serviceId, Incident candidate) {
        double score = TextSimilarity.incidentSimilarity(
                title,
                description,
                candidate.getTitle(),
                candidate.getDescription()
        );
        if (serviceId != null && serviceId.equals(candidate.getService().getId())) {
            score += SAME_SERVICE_BONUS;
        }
        return Math.min(1.0, Math.round(score * 100.0) / 100.0);
    }
}
