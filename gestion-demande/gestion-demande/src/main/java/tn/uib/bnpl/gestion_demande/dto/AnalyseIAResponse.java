package tn.uib.bnpl.gestion_demande.dto;

import java.util.List;
import java.util.Map;

/**
 * Réponse HTTP 200 de POST /api/demandes/analyse-ia.
 */
public record AnalyseIAResponse(
        List<String> recommandations,
        Map<String, Object> corrections,
        List<String> alertes,
        Integer scoreCoherence
) {}
