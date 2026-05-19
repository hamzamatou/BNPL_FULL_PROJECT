 
// ─── AnalyseIAResponse.java ───────────────────────────────────────────────────
package tn.uib.bnpl.gestion_demande.dto;
 
import java.util.List;
 
/**
 * Réponse de l'endpoint POST /api/demandes/analyse-ia.
 * Retournée au front AVANT toute persistence.
 */
public class AnalyseIAResponse {
 
    /** true si le service Python n'a détecté aucune anomalie OCR. */
    private boolean coherent;
 
    /**
     * Liste des anomalies OCR (string[] brut du service Python).
     * Vide si cohérence OK.
     */
    private List<String> anomalies;
 
    /**
     * Recommandations financières (string[] de RecommandationResult.recommandations).
     * Null si cohérence KO (on ne calcule pas la recommandation si le dossier est incohérent).
     */
    private List<String> recommandations;
 
    // ── Factory ──────────────────────────────────────────────────────────
 
    public static AnalyseIAResponse coherenceKo(List<String> anomalies) {
        AnalyseIAResponse r = new AnalyseIAResponse();
        r.coherent       = false;
        r.anomalies      = anomalies;
        r.recommandations = null;
        return r;
    }
 
    public static AnalyseIAResponse ok(List<String> recommandations) {
        AnalyseIAResponse r = new AnalyseIAResponse();
        r.coherent        = true;
        r.anomalies       = List.of();
        r.recommandations = recommandations != null ? recommandations : List.of();
        return r;
    }
 
    // ── Getters / Setters ─────────────────────────────────────────────────
 
    public boolean       isCoherent()                      { return coherent; }
    public void          setCoherent(boolean v)            { this.coherent = v; }
 
    public List<String>  getAnomalies()                    { return anomalies; }
    public void          setAnomalies(List<String> v)      { this.anomalies = v; }
 
    public List<String>  getRecommandations()              { return recommandations; }
    public void          setRecommandations(List<String> v){ this.recommandations = v; }
}