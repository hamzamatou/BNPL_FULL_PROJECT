package tn.uib.bnpl.gestion_demande.dto;

import java.util.List;

/**
 * Réponse HTTP 200 de POST /api/demandes/recommandations.
 */
public record RecommandationsResponse(
        List<String> recommandations,
        String processInstanceId
) {
}
