package tn.uib.bnpl.gestion_demande.exceptions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lancée par AnalyseIAService quand le service Python détecte des anomalies bloquantes.
 * Le contrôleur la traduit en HTTP 422 avec { message, anomalies, corrections? }.
 */
public class CoherenceAnomalyException extends RuntimeException {

    private final List<String> anomalies;
    private final Map<String, Object> corrections;

    public CoherenceAnomalyException(String message, List<String> anomalies) {
        this(message, anomalies, Map.of());
    }

    public CoherenceAnomalyException(String message, List<String> anomalies, Map<String, Object> corrections) {
        super(message);
        this.anomalies = anomalies != null ? anomalies : List.of();
        this.corrections = corrections != null ? corrections : Map.of();
    }

    public List<String> getAnomalies() { return anomalies; }

    public Map<String, Object> getCorrections() {
        return Collections.unmodifiableMap(corrections);
    }
}