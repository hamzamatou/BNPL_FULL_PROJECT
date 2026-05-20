package tn.uib.bnpl.gestion_utilisateur.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.uib.bnpl.gestion_utilisateur.dto.audit.AuditEventRequest;

/**
 * Appels HTTP vers reporting-archivage (équivalent Feign, compatible Spring Boot 4).
 */
@Component
public class ReportingArchivageClient {

    private final RestClient restClient;

    public ReportingArchivageClient(RestClient reportingArchivageRestClient) {
        this.restClient = reportingArchivageRestClient;
    }

    public void publierEvenementAudit(AuditEventRequest request) {
        restClient.post()
                .uri("/api/internal/audit/events")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
