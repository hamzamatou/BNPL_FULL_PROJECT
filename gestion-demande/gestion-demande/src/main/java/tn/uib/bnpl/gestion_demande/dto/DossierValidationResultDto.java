package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Réponse POST /dossier/validate (micro IA).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DossierValidationResultDto(
        @JsonProperty("score_coherence") Integer scoreCoherence,
        @JsonProperty("anomalies") List<CoherenceResultDto.AnomalieDto> anomalies,
        @JsonProperty("corrections") Map<String, Object> corrections,
        @JsonProperty("recommandations") List<String> recommandations,
        @JsonProperty("message") String message,
        @JsonProperty("documents_manquants") List<String> documentsManquants
) {
    public boolean hasAucuneAnomalie() {
        return anomalies == null || anomalies.isEmpty();
    }

    public List<String> anomalieMessages() {
        if (anomalies == null) return List.of();
        return anomalies.stream().map(CoherenceResultDto.AnomalieDto::message).toList();
    }
}
