package tn.uib.bnpl.gestion_demande.client;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tn.uib.bnpl.gestion_demande.dto.ActionDemandeHistoriqueViewDto;
import tn.uib.bnpl.gestion_demande.dto.ArchivageDemandeRequest;

import java.util.List;

@Component
public class ReportingArchivageClient {

    private static final ParameterizedTypeReference<List<ActionDemandeHistoriqueViewDto>> ACTIONS_LIST =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;

    public ReportingArchivageClient(RestClient reportingArchivageRestClient) {
        this.restClient = reportingArchivageRestClient;
    }

    public void archiverDemande(ArchivageDemandeRequest request) {
        restClient.post()
                .uri("/api/internal/archivage/dossiers")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public List<ActionDemandeHistoriqueViewDto> listerActionsDemande(Long demandeId) {
        return restClient.get()
                .uri("/api/internal/audit/demandes/{demandeId}/actions", demandeId)
                .retrieve()
                .body(ACTIONS_LIST);
    }
}
