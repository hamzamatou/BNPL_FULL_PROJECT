package tn.uib.bnpl.gestion_demande.dto;
 
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
 
import java.util.List;
 
/**
 * Réponse de GET /recommendation/generate (service Python).
 * Aligné sur RecommandationResult.to_dict() de service_recommendation.py.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommandationResultDto(
 
        @JsonProperty("conforme")               Boolean conforme,
        @JsonProperty("mensualite_bnpl")         Double mensualiteBnpl,
        @JsonProperty("revenu_disponible")       Double revenuDisponible,
        @JsonProperty("plafond_bnpl")            Double plafondBnpl,
        @JsonProperty("montant_max_acceptable")  Double montantMaxAcceptable,
        @JsonProperty("duree_minimale_mois")     Integer dureeMinimaleMois,
        @JsonProperty("score_solvabilite")       String scoreSolvabilite,
        @JsonProperty("evaluation")              String evaluation,
        @JsonProperty("recommandations")         List<String> recommandations,
        @JsonProperty("texte_complet")           String texteComplet,
        @JsonProperty("raw_llm_response")        String rawLlmResponse
) {}