package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Champs structurés persistés dans {@code action_demande_historique.detailsJson}
 * pour la traçabilité IA (score, explications, recommandations).
 */
public final class HistoriqueIaDetails {

    private HistoriqueIaDetails() {
    }

    public static Map<String, Object> prescoring(Integer score,
                                            Double probabiliteDefaut,
                                            String zoneCode,
                                            List<String> explications) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (score != null) {
            map.put("score", score);
        }
        if (probabiliteDefaut != null) {
            map.put("probabiliteDefaut", probabiliteDefaut);
        }
        if (zoneCode != null && !zoneCode.isBlank()) {
            map.put("zoneCode", zoneCode);
        }
        if (explications != null && !explications.isEmpty()) {
            map.put("explications", explications);
        }
        return map;
    }

    public static Map<String, Object> prescoringFromEntity(tn.uib.bnpl.gestion_demande.classes.PrescoringScore score,
                                                    ObjectMapper objectMapper) {
        if (score == null) {
            return Map.of();
        }
        return prescoring(
                score.getScore(),
                score.getProbabiliteDefaut(),
                score.getZoneCode(),
                parseExplications(score.getExplicationsJson(), objectMapper)
        );
    }

    public static Map<String, Object> recommandations(List<String> recommandations) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (recommandations != null && !recommandations.isEmpty()) {
            map.put("recommandations", recommandations);
        }
        return map;
    }

    static List<String> parseExplications(String explicationsJson, ObjectMapper objectMapper) {
        if (explicationsJson == null || explicationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(explicationsJson, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of(explicationsJson);
        }
    }
}
