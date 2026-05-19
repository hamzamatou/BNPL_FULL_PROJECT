package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
/**
 * Réponse de GET /prescoring/prescore (service Python).
 * Aligné sur prescore_dossier() de prescoring_service.py.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrescoringResultDto(
 
        @JsonProperty("pd_pct")       Double pdPct,
        @JsonProperty("score")        Integer score,
        @JsonProperty("zone")         ZoneDto zone,
        @JsonProperty("alertes")      List<String> alertes,
        @JsonProperty("explications") List<String> explications,
        @JsonProperty("defaut")       Boolean defaut,
        @JsonProperty("seuil_pd_pct") Double seuilPdPct
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ZoneDto(
            @JsonProperty("code")    String code,
            @JsonProperty("couleur") String couleur,
            @JsonProperty("libelle") String libelle
    ) {}
}