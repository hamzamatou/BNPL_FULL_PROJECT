package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Fusion de {@code AffectationDemandeBanque} et {@code TraitementDemande} : une ligne par couple
 * (demande, banque utilisateur). Verrouillage, expiration et décision métier sur la même entité.
 */
@Entity
@Table(
        name = "prise_en_charge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"demande_id", "banque_user_id"})
)
public class PriseEnCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "demande_id", nullable = false)
    private DemandeFinancement demande;

    @Column(name = "banque_user_id", nullable = false)
    private Long banqueUserId;

    @Column(nullable = false)
    private Integer scoreInterne;

    /**
     * ROUTE, VERROUILLEE, EXPIRE, DEVERROUILLEE.
     * Fenêtre 48 h : {@link #dateExpiration} tant que {@link #decision} est null ; annulée au démarrage d'analyse.
     */
    @Column(nullable = false)
    private String statut;

    private LocalDateTime dateVerrouillage;
    private LocalDateTime dateExpiration;

    /**
     * EN_COURS, ACCEPTEE, REFUSEE — état décisionnel (ex-{@code TraitementDemande.decision}).
     */
    private String decision;

    private String motifRefus;

    @Column(length = 4000)
    private String commentaire;

    private LocalDateTime dateDebutTraitement;
    private LocalDateTime dateDecision;

    protected PriseEnCharge() {
    }

    public PriseEnCharge(DemandeFinancement demande,
                         Long banqueUserId,
                         Integer scoreInterne,
                         String statut) {
        this.demande = demande;
        this.banqueUserId = banqueUserId;
        this.scoreInterne = scoreInterne;
        this.statut = statut;
    }

    public Long getId() {
        return id;
    }

    public DemandeFinancement getDemande() {
        return demande;
    }

    public void setDemande(DemandeFinancement demande) {
        this.demande = demande;
    }

    public Long getBanqueUserId() {
        return banqueUserId;
    }

    public void setBanqueUserId(Long banqueUserId) {
        this.banqueUserId = banqueUserId;
    }

    public Integer getScoreInterne() {
        return scoreInterne;
    }

    public void setScoreInterne(Integer scoreInterne) {
        this.scoreInterne = scoreInterne;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDateTime getDateVerrouillage() {
        return dateVerrouillage;
    }

    public void setDateVerrouillage(LocalDateTime dateVerrouillage) {
        this.dateVerrouillage = dateVerrouillage;
    }

    public LocalDateTime getDateExpiration() {
        return dateExpiration;
    }

    public void setDateExpiration(LocalDateTime dateExpiration) {
        this.dateExpiration = dateExpiration;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getMotifRefus() {
        return motifRefus;
    }

    public void setMotifRefus(String motifRefus) {
        this.motifRefus = motifRefus;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public LocalDateTime getDateDebutTraitement() {
        return dateDebutTraitement;
    }

    public void setDateDebutTraitement(LocalDateTime dateDebutTraitement) {
        this.dateDebutTraitement = dateDebutTraitement;
    }

    public LocalDateTime getDateDecision() {
        return dateDecision;
    }

    public void setDateDecision(LocalDateTime dateDecision) {
        this.dateDecision = dateDecision;
    }
}
