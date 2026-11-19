package com.example.itsm.intelligence;

import com.example.itsm.catalog.Category;
import com.example.itsm.catalog.CategoryRepository;
import com.example.itsm.catalog.ItServiceRepository;
import com.example.itsm.incident.IncidentAccessPolicy;
import com.example.itsm.incident.dto.DuplicateResponse;
import com.example.itsm.security.CurrentUserService;
import com.example.itsm.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/intelligence")
public class IntelligenceController {

    private static final int PAST_FIX_LIMIT = 3;

    private final CategorizationService categorizationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final ResolutionSuggestionService resolutionSuggestionService;
    private final ServiceSuggestionService serviceSuggestionService;
    private final CategoryRepository categoryRepository;
    private final ItServiceRepository serviceRepository;
    private final CurrentUserService currentUserService;
    private final IncidentAccessPolicy accessPolicy;

    public IntelligenceController(
            CategorizationService categorizationService,
            DuplicateDetectionService duplicateDetectionService,
            ResolutionSuggestionService resolutionSuggestionService,
            ServiceSuggestionService serviceSuggestionService,
            CategoryRepository categoryRepository,
            ItServiceRepository serviceRepository,
            CurrentUserService currentUserService,
            IncidentAccessPolicy accessPolicy
    ) {
        this.categorizationService = categorizationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.resolutionSuggestionService = resolutionSuggestionService;
        this.serviceSuggestionService = serviceSuggestionService;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.currentUserService = currentUserService;
        this.accessPolicy = accessPolicy;
    }

    /**
     * Preview shown on the report form: suggested category and affected service (which the form fills
     * in), possible duplicates and similar past fixes.
     */
    @PostMapping("/classify")
    public IntelligencePreview classify(@Valid @RequestBody IntelligencePreview.ClassifyRequest request) {
        List<Category> categories = categoryRepository.findByActiveTrueOrderByNameAsc();
        ClassificationSuggestion suggestion = categorizationService.suggest(
                request.title(),
                request.description(),
                categories
        );
        ServiceSuggestion service = serviceSuggestionService.suggest(
                request.title(),
                request.description(),
                serviceRepository.findByActiveTrueOrderByNameAsc()
        ).orElse(null);
        // Until the reporter picks a service, matching uses the suggested one.
        Long serviceId = request.serviceId() != null ? request.serviceId()
                : service != null ? service.serviceId() : null;

        UserPrincipal viewer = currentUserService.requirePrincipal();
        List<DuplicateMatch> duplicateMatches = duplicateDetectionService.findMatches(
                request.title(),
                request.description(),
                serviceId,
                null
        );
        // Someone else's similar report is mentioned, but without its title, unless the viewer may open it.
        List<DuplicateResponse> duplicates = duplicateMatches.stream()
                .map(match -> DuplicateResponse.of(match, accessPolicy.canView(match.incident(), viewer)))
                .toList();

        // Similar past fixes, so the reporter can try a known fix before submitting. An incident
        // already listed as a possible duplicate is not repeated here.
        Set<Long> duplicateIds = duplicateMatches.stream()
                .map(match -> match.incident().getId())
                .collect(Collectors.toSet());
        List<PastFixResponse> pastFixes = resolutionSuggestionService.findPastFixes(
                        request.title(),
                        request.description(),
                        serviceId,
                        null,
                        ResolutionSuggestionService.REPORTER_THRESHOLD,
                        PAST_FIX_LIMIT + duplicateIds.size()
                ).stream()
                .filter(match -> !duplicateIds.contains(match.incident().getId()))
                .limit(PAST_FIX_LIMIT)
                .map(match -> PastFixResponse.of(match, accessPolicy.canView(match.incident(), viewer)))
                .toList();
        return new IntelligencePreview(suggestion, service, duplicates, pastFixes);
    }
}
