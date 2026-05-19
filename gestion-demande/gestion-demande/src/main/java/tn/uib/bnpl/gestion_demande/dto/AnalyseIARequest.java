// ─── AnalyseIARequest.java ────────────────────────────────────────────────────
package tn.uib.bnpl.gestion_demande.dto;
 
import org.springframework.web.multipart.MultipartFile;
 
import java.math.BigDecimal;
import java.util.List;
 
/**
 * Requête de l'endpoint GET /api/demandes/analyse-ia.
 * Contient les données déclarées + les fichiers pour l'OCR.
 * Aucune persistence — analyse seule.
 */
public class AnalyseIARequest {
 
    // Données déclarées (pour construire le declared_data JSON vers Python)
    private String     cin;
    private BigDecimal revenuMensuelNet;
    private BigDecimal revenuAnnuel;
    private String     typeContrat;
    private Integer    ancienneteEmploiMois;
    private BigDecimal montant;
 
    // Données financières pour la recommandation
    private BigDecimal autresRevenusMensuels;
    private BigDecimal mensualitesCredits;
    private BigDecimal autresChargesFixes;
    private BigDecimal loyerMensuel;
    private BigDecimal encoursCredits;
    private Integer    nombreEnfants;
    private Integer    dureeMois;
 
    // Fichiers pour l'OCR (mêmes que pour la création)
    private List<DocumentEntry> documents;
 
    public static class DocumentEntry {
        private String        typeDocument;
        private MultipartFile file;
 
        public String        getTypeDocument() { return typeDocument; }
        public void          setTypeDocument(String v) { this.typeDocument = v; }
        public MultipartFile getFile()         { return file; }
        public void          setFile(MultipartFile v) { this.file = v; }
    }
 
    // ── Getters / Setters ─────────────────────────────────────────────────
 
    public String     getCin()                   { return cin; }
    public void       setCin(String v)            { this.cin = v; }
 
    public BigDecimal getRevenuMensuelNet()       { return revenuMensuelNet; }
    public void       setRevenuMensuelNet(BigDecimal v) { this.revenuMensuelNet = v; }
 
    public BigDecimal getRevenuAnnuel()           { return revenuAnnuel; }
    public void       setRevenuAnnuel(BigDecimal v) { this.revenuAnnuel = v; }
 
    public String     getTypeContrat()            { return typeContrat; }
    public void       setTypeContrat(String v)    { this.typeContrat = v; }
 
    public Integer    getAncienneteEmploiMois()   { return ancienneteEmploiMois; }
    public void       setAncienneteEmploiMois(Integer v) { this.ancienneteEmploiMois = v; }
 
    public BigDecimal getMontant()                { return montant; }
    public void       setMontant(BigDecimal v)    { this.montant = v; }
 
    public BigDecimal getAutresRevenusMensuels()  { return autresRevenusMensuels; }
    public void       setAutresRevenusMensuels(BigDecimal v) { this.autresRevenusMensuels = v; }
 
    public BigDecimal getMensualitesCredits()     { return mensualitesCredits; }
    public void       setMensualitesCredits(BigDecimal v) { this.mensualitesCredits = v; }
 
    public BigDecimal getAutresChargesFixes()     { return autresChargesFixes; }
    public void       setAutresChargesFixes(BigDecimal v) { this.autresChargesFixes = v; }
 
    public BigDecimal getLoyerMensuel()           { return loyerMensuel; }
    public void       setLoyerMensuel(BigDecimal v) { this.loyerMensuel = v; }
 
    public BigDecimal getEncoursCredits()         { return encoursCredits; }
    public void       setEncoursCredits(BigDecimal v) { this.encoursCredits = v; }
 
    public Integer    getNombreEnfants()          { return nombreEnfants; }
    public void       setNombreEnfants(Integer v) { this.nombreEnfants = v; }
 
    public Integer    getDureeMois()              { return dureeMois; }
    public void       setDureeMois(Integer v)     { this.dureeMois = v; }
 
    public List<DocumentEntry> getDocuments()    { return documents; }
    public void setDocuments(List<DocumentEntry> v) { this.documents = v; }
}