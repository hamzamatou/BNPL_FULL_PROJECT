package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoutageEvaluerResponse(
        @JsonProperty("banquesRoutees") List<String> banquesRoutees
) {
}
