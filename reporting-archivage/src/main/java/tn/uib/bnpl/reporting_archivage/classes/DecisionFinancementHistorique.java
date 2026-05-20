package tn.uib.bnpl.reporting_archivage.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "decision_financement_historique", indexes = {
        @Index(name = "idx_decision_demande", columnList = "demandeId"),
        @Index(name = "idx_decision_date", columnList = "dateDecision")
})
public class DecisionFinancementHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long demandeId;

    private String referenceDemande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeDecisionFinancement typeDecision;

    @Column(nullable = false, length = 500)
    private String libelle;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    private Long acteurUserId;
    private String acteurEmail;
    private String acteurRole;
    private String etapeWorkflow;
    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime dateDecision;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnregistrement;

    public DecisionFinancementHistorique() {
    }

    @PrePersist
    void prePersist() {
        if (dateEnregistrement == null) {
            dateEnregistrement = LocalDateTime.now();
        }
        if (dateDecision == null) {
            dateDecision = dateEnregistrement;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }
    public String getReferenceDemande() { return referenceDemande; }
    public void setReferenceDemande(String referenceDemande) { this.referenceDemande = referenceDemande; }
    public TypeDecisionFinancement getTypeDecision() { return typeDecision; }
    public void setTypeDecision(TypeDecisionFinancement typeDecision) { this.typeDecision = typeDecision; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public Long getActeurUserId() { return acteurUserId; }
    public void setActeurUserId(Long acteurUserId) { this.acteurUserId = acteurUserId; }
    public String getActeurEmail() { return acteurEmail; }
    public void setActeurEmail(String acteurEmail) { this.acteurEmail = acteurEmail; }
    public String getActeurRole() { return acteurRole; }
    public void setActeurRole(String acteurRole) { this.acteurRole = acteurRole; }
    public String getEtapeWorkflow() { return etapeWorkflow; }
    public void setEtapeWorkflow(String etapeWorkflow) { this.etapeWorkflow = etapeWorkflow; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDateTime dateDecision) { this.dateDecision = dateDecision; }
    public LocalDateTime getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDateTime dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
}
