package tn.uib.bnpl.gestion_demande.exceptions;
 
import java.util.List;
 
/**
 * Lancée par AnalyseIAService quand le service Python détecte des anomalies bloquantes.
 * Le contrôleur la traduit en HTTP 422 avec { message, anomalies: string[] }.
 */
public class CoherenceAnomalyException extends RuntimeException {
 
    private final List<String> anomalies;
 
    public CoherenceAnomalyException(String message, List<String> anomalies) {
        super(message);
        this.anomalies = anomalies != null ? anomalies : List.of();
    }
 
    public List<String> getAnomalies() { return anomalies; }
}