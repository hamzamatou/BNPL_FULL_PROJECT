package tn.uib.bnpl.gestion_demande.dto;

import java.util.List;
import java.util.Map;

/**
 * Réponse HTTP 200 de POST /api/demandes/coherence.
 * <p>Micro cohérence : anomalies + corrections. Si aucune anomalie bloquante,
 * Spring appelle GET /recommendation/generate et renseigne {@code recommandations}.
 * <p>HTTP 422 : voir {@link tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException}
 * (anomalies + corrections uniquement).
 */
public record CoherenceResponse(
        boolean coherent,
        String processInstanceId,
        String analysisSessionId,
        Map<String, Object> corrections,
        List<String> alertes,
        List<String> recommandations
) {
    public CoherenceResponse(
            boolean coherent,
            String processInstanceId,
            String analysisSessionId
    ) {
        this(coherent, processInstanceId, analysisSessionId, Map.of(), List.of(), List.of());
    }

    public CoherenceResponse(
            boolean coherent,
            String processInstanceId,
            String analysisSessionId,
            Map<String, Object> corrections,
            List<String> alertes
    ) {
        this(coherent, processInstanceId, analysisSessionId, corrections, alertes, List.of());
    }
}
