// ─────────────────────────────────────────────────────────────────────────────
// CoherenceResultDto.java
// ─────────────────────────────────────────────────────────────────────────────
package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Réponse de POST /coherence/check (service Python).
 *
 * Cas succès  : { anomalies: [], corrections: {} }
 * Cas anomalie: { anomalies: [{code, niveau, message}], corrections }
 * Cas bloquant: { anomalies: [{niveau:"BLOQUANT",…}], corrections: {} }
 * Cas docs manquants : { message, documents_manquants: [] }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoherenceResultDto(

        @JsonProperty("anomalies")          List<AnomalieDto> anomalies,
        @JsonProperty("corrections")        Map<String, Object> corrections,

        // Retourné uniquement si documents manquants
        @JsonProperty("message")            String message,
        @JsonProperty("documents_manquants") List<String> documentsManquants
) {
    /** Vrai si le tableau anomalies est vide (aucun écart signalé). */
    public boolean hasAucuneAnomalie() {
        return anomalies == null || anomalies.isEmpty();
    }

    /** Vrai si le dossier est conforme (pas d'anomalie bloquante). */
    public boolean isConforme() {
        if (hasAucuneAnomalie()) return true;
        return anomalies.stream().noneMatch(a -> "BLOQUANT".equalsIgnoreCase(a.niveau()));
    }

    /** Retourne les messages d'anomalie pour l'affichage Angular. */
    public List<String> anomalieMessages() {
        if (anomalies == null) return List.of();
        return anomalies.stream().map(AnomalieDto::message).toList();
    }

    /** Messages des anomalies non bloquantes (niveau ALERTE). */
    public List<String> alerteMessages() {
        if (anomalies == null) return List.of();
        return anomalies.stream()
                .filter(a -> a.niveau() != null && !"BLOQUANT".equalsIgnoreCase(a.niveau()))
                .map(AnomalieDto::message)
                .toList();
    }

    public List<String> anomaliesBloquantesMessages() {
        if (anomalies == null) return List.of();
        return anomalies.stream()
                .filter(a -> "BLOQUANT".equalsIgnoreCase(a.niveau()))
                .map(AnomalieDto::message)
                .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnomalieDto(
            @JsonProperty("code")     String code,
            @JsonProperty("niveau")   String niveau,
            @JsonProperty("message")  String message,
            @JsonProperty("details")  Map<String, Object> details
    ) {}
}