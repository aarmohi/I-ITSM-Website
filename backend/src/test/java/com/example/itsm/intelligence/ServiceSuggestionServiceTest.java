package com.example.itsm.intelligence;

import com.example.itsm.catalog.BusinessCriticality;
import com.example.itsm.catalog.ItService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceSuggestionServiceTest {

    private final ServiceSuggestionService service = new ServiceSuggestionService();
    private final List<ItService> services = List.of(
            new ItService("PAYMENT_GATEWAY", "Payment Gateway", "Customer card payments and checkout processing.", BusinessCriticality.CRITICAL),
            new ItService("VPN", "Remote VPN", "Remote connectivity for staff and contractors.", BusinessCriticality.HIGH),
            new ItService("EMAIL_COLLABORATION", "Email & Collaboration", "Mail, calendar and collaboration tools.", BusinessCriticality.HIGH),
            new ItService("DATABASE_CLUSTER", "Database Cluster", "Primary and replica database infrastructure.", BusinessCriticality.CRITICAL),
            new ItService("END_USER_COMPUTING", "End-User Computing", "Laptops, desktops and peripherals.", BusinessCriticality.LOW)
    );

    @Test
    void suggestsTheServiceWhoseWordsTheReportUses() {
        Optional<ServiceSuggestion> suggestion = service.suggest(
                "Card payments failing",
                "Customers get a 503 error from the payment gateway when paying by card.",
                services
        );

        assertThat(suggestion).isPresent();
        assertThat(suggestion.get().serviceName()).isEqualTo("Payment Gateway");
        assertThat(suggestion.get().rationale()).contains("“card”", "“gateway”");
        assertThat(suggestion.get().confidence()).isGreaterThan(0.5);
    }

    @Test
    void matchesPluralsAndIgnoresGenericWords() {
        // "laptops" in the service matches "laptop"; "users" must not pull in End-User Computing on its own.
        assertThat(service.suggest("My laptop will not charge", "The battery stays at zero percent.", services))
                .map(ServiceSuggestion::serviceName)
                .contains("End-User Computing");
        assertThat(service.suggest("Slow responses", "Several users say pages are slow today.", services))
                .isEmpty();
    }

    @Test
    void knowsTheEverydayWordsForStandardServices() {
        // Neither "mouse" nor "Outlook" is in a service description; the keyword lists cover them.
        assertThat(service.suggest("Mouse on desk 414 is not working", "Mouse on desk 414 is not working", services))
                .map(ServiceSuggestion::serviceName)
                .contains("End-User Computing");
        assertThat(service.suggest("Outlook will not open", "It crashes on start every time.", services))
                .map(ServiceSuggestion::serviceName)
                .contains("Email & Collaboration");
    }

    @Test
    void makesNoSuggestionWhenTwoServicesTie() {
        assertThat(service.suggest("Problem", "Both email and the database are affected.", services)).isEmpty();
    }
}
