package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Réponse de GET /recommendation/generate (micro Python).
 * Corps attendu : {@code {"recommandations": ["...", "..."]}}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommandationResultDto(
        @JsonProperty("recommandations") List<String> recommandations
) {}
