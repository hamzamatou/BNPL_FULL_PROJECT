package tn.uib.bnpl.reporting_archivage.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_demande_historique", indexes = {
        @Index(name = "idx_action_demande", columnList = "demandeId"),
        @Index(name = "idx_action_demande_date", columnList = "dateAction")
})
public class ActionDemandeHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long demandeId;

    private String referenceDemande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeActionDemande typeAction;

    @Column(nullable = false, length = 500)
    private String libelle;

    private String statutAvant;
    private String statutApres;
    private Long acteurUserId;
    private String acteurEmail;
    private String acteurRole;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime dateAction;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnregistrement;

    public ActionDemandeHistorique() {
    }

    @PrePersist
    void prePersist() {
        if (dateEnregistrement == null) {
            dateEnregistrement = LocalDateTime.now();
        }
        if (dateAction == null) {
            dateAction = dateEnregistrement;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }
    public String getReferenceDemande() { return referenceDemande; }
    public void setReferenceDemande(String referenceDemande) { this.referenceDemande = referenceDemande; }
    public TypeActionDemande getTypeAction() { return typeAction; }
    public void setTypeAction(TypeActionDemande typeAction) { this.typeAction = typeAction; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getStatutAvant() { return statutAvant; }
    public void setStatutAvant(String statutAvant) { this.statutAvant = statutAvant; }
    public String getStatutApres() { return statutApres; }
    public void setStatutApres(String statutApres) { this.statutApres = statutApres; }
    public Long getActeurUserId() { return acteurUserId; }
    public void setActeurUserId(Long acteurUserId) { this.acteurUserId = acteurUserId; }
    public String getActeurEmail() { return acteurEmail; }
    public void setActeurEmail(String acteurEmail) { this.acteurEmail = acteurEmail; }
    public String getActeurRole() { return acteurRole; }
    public void setActeurRole(String acteurRole) { this.acteurRole = acteurRole; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
    public LocalDateTime getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDateTime dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
}
