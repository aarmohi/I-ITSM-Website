package com.example.itsm.intelligence;

import com.example.itsm.catalog.ItService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Suggests the affected service from the report text, so the report form can fill it in. Rule-based
 * and explainable like {@link CategorizationService}: a service scores one point for each of its words
 * that the report uses, and an extra point when that word is in the title. A service's words are its
 * name and description (so services an admin adds work too) plus, for the standard catalogue, the
 * everyday words people use for it ("mouse", "Outlook", "password"). No suggestion is made when
 * nothing matches or two services tie, because a wrong service would give the wrong criticality, and
 * with it the wrong priority.
 */
@Service
public class ServiceSuggestionService {

    /** Words that appear in many service descriptions and say nothing about which service is meant. */
    private static final Set<String> GENERIC_WORDS = Set.of(
            "service", "customer", "staff", "primary", "tool", "processing", "infrastructure",
            "employee", "shared", "object", "single", "self", "workflow", "contractor", "end", "user"
    );

    /** Everyday words for the standard services, keyed by service code (singular, lower case). */
    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "PAYMENT_GATEWAY", List.of("payment", "card", "checkout", "gateway", "transaction", "refund", "upi"),
            "IDENTITY_SSO", List.of("login", "password", "sso", "mfa", "otp", "sign", "authentication", "locked"),
            "DATABASE_CLUSTER", List.of("database", "sql", "query", "replica", "replication", "postgres", "deadlock"),
            "VPN", List.of("vpn", "remote", "tunnel", "anyconnect"),
            "EMAIL_COLLABORATION", List.of("email", "mail", "mailbox", "outlook", "calendar", "inbox", "phishing"),
            "CLOUD_STORAGE", List.of("bucket", "storage", "drive", "quota", "upload", "onedrive", "sharepoint"),
            "HR_PORTAL", List.of("payroll", "payslip", "appraisal", "attendance", "reimbursement"),
            "END_USER_COMPUTING", List.of("mouse", "keyboard", "monitor", "printer", "laptop", "desktop", "screen",
                    "headset", "webcam", "dock", "battery", "charger", "peripheral")
    );

    public Optional<ServiceSuggestion> suggest(String title, String description, List<ItService> services) {
        Set<String> reportWords = words(title + " " + description);
        Set<String> titleWords = words(title);
        if (reportWords.isEmpty() || services == null || services.isEmpty()) {
            return Optional.empty();
        }

        List<Scored> ranked = services.stream()
                .map(service -> score(service, reportWords, titleWords))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .toList();
        if (ranked.isEmpty() || (ranked.size() > 1 && ranked.get(0).score() == ranked.get(1).score())) {
            return Optional.empty();
        }

        Scored best = ranked.getFirst();
        double confidence = Math.min(0.9, 0.45 + best.score() * 0.15);
        String matched = best.matched().stream().map(word -> "“" + word + "”").collect(Collectors.joining(", "));
        return Optional.of(new ServiceSuggestion(
                best.service().getId(),
                best.service().getName(),
                Math.round(confidence * 100.0) / 100.0,
                "The report mentions %s, which points to %s.".formatted(matched, best.service().getName())
        ));
    }

    private Scored score(ItService service, Set<String> reportWords, Set<String> titleWords) {
        Set<String> serviceWords = words(service.getName() + " " + service.getDescription());
        serviceWords.removeAll(GENERIC_WORDS);
        serviceWords.addAll(KEYWORDS.getOrDefault(service.getCode(), List.of()));
        Set<String> matched = new LinkedHashSet<>(serviceWords);
        matched.retainAll(reportWords);
        int score = matched.size() + (int) matched.stream().filter(titleWords::contains).count();
        return new Scored(service, score, matched);
    }

    /** Lower-case content words, with a simple plural "s" removed so "laptops" matches "laptop". */
    static Set<String> words(String text) {
        return TextSimilarity.tokens(text).stream()
                .map(word -> word.length() > 4 && word.endsWith("s") && !word.endsWith("ss")
                        ? word.substring(0, word.length() - 1)
                        : word)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record Scored(ItService service, int score, Set<String> matched) {
    }
}
