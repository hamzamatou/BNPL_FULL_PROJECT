package tn.uib.bnpl.gestion_demande.exceptions;

import tn.uib.bnpl.gestion_demande.dto.CoherenceResultDto.AnomalieDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lancée par AnalyseIAService quand le service Python détecte des anomalies bloquantes.
 * Le contrôleur la traduit en HTTP 422 avec { message, anomalies, corrections? }.
 */
public class CoherenceAnomalyException extends RuntimeException {

    private final List<AnomalieDto> anomalyItems;
    private final Map<String, Object> corrections;

    public CoherenceAnomalyException(
            String message,
            List<AnomalieDto> anomalyItems,
            Map<String, Object> corrections
    ) {
        super(message);
        this.anomalyItems = anomalyItems != null ? anomalyItems : List.of();
        this.corrections = corrections != null ? corrections : Map.of();
    }

    public static CoherenceAnomalyException fromMessages(
            String message,
            List<String> anomalyMessages,
            Map<String, Object> corrections
    ) {
        List<AnomalieDto> items = anomalyMessages != null
                ? anomalyMessages.stream()
                    .map(m -> new AnomalieDto(null, "BLOQUANT", m, null))
                    .collect(Collectors.toList())
                : List.of();
        return new CoherenceAnomalyException(message, items, corrections);
    }

    /** Messages texte (workflow Camunda). */
    public List<String> getAnomalies() {
        return anomalyItems.stream().map(AnomalieDto::message).toList();
    }

    public List<AnomalieDto> getAnomalyItems() {
        return Collections.unmodifiableList(anomalyItems);
    }

    public Map<String, Object> getCorrections() {
        return Collections.unmodifiableMap(corrections);
    }
}
