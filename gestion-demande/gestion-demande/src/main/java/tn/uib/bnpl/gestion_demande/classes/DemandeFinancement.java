package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "demande_financement")
public class DemandeFinancement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_client_id", nullable = false)
    private DossierClient dossierClient;

    @Column(nullable = false)
    private Long commercantUserId;

    @Column(nullable = false, unique = true)
    private String referenceDemande;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal montant;

    @Column(nullable = false)
    private Integer dureeMois;

    @Column(nullable = false)
    private String statut;

    private LocalDateTime dateCreation;
    private LocalDateTime dateDerniereMiseAJour;
    private String typeProduit;

    @OneToMany(mappedBy = "demande", fetch = FetchType.LAZY)
    private List<PriseEnCharge> prisesEnCharge;

    /** Recommandation persistée juste après création (avant consentement). */
    @OneToOne(mappedBy = "demande", cascade = CascadeType.ALL,
              fetch = FetchType.LAZY, optional = true)
    private Recommandation recommandation;

    /** Score prescoring persisté après consentement du client. */
    @OneToOne(mappedBy = "demande", cascade = CascadeType.ALL,
              fetch = FetchType.LAZY, optional = true)
    private PrescoringScore prescoringScore;

    // ─── Getters / Setters ────────────────────────────────────────────────────

    public Long getId()                                    { return id; }

    public DossierClient getDossierClient()                { return dossierClient; }
    public void setDossierClient(DossierClient v)          { this.dossierClient = v; }

    public Long getCommercantUserId()                      { return commercantUserId; }
    public void setCommercantUserId(Long v)                { this.commercantUserId = v; }

    public String getReferenceDemande()                    { return referenceDemande; }
    public void setReferenceDemande(String v)              { this.referenceDemande = v; }

    public BigDecimal getMontant()                         { return montant; }
    public void setMontant(BigDecimal v)                   { this.montant = v; }

    public Integer getDureeMois()                          { return dureeMois; }
    public void setDureeMois(Integer v)                    { this.dureeMois = v; }

    public String getStatut()                              { return statut; }
    public void setStatut(String v)                        { this.statut = v; }

    public LocalDateTime getDateCreation()                 { return dateCreation; }
    public void setDateCreation(LocalDateTime v)           { this.dateCreation = v; }

    public LocalDateTime getDateDerniereMiseAJour()        { return dateDerniereMiseAJour; }
    public void setDateDerniereMiseAJour(LocalDateTime v)  { this.dateDerniereMiseAJour = v; }

    public String getTypeProduit()                         { return typeProduit; }
    public void setTypeProduit(String v)                   { this.typeProduit = v; }

    public List<PriseEnCharge> getPrisesEnCharge()         { return prisesEnCharge; }
    public void setPrisesEnCharge(List<PriseEnCharge> v)   { this.prisesEnCharge = v; }

    public Recommandation getRecommandation()              { return recommandation; }
    public void setRecommandation(Recommandation v)        { this.recommandation = v; }

    public PrescoringScore getPrescoringScore()            { return prescoringScore; }
    public void setPrescoringScore(PrescoringScore v)      { this.prescoringScore = v; }

    // ─── Constructeurs ────────────────────────────────────────────────────────

    public DemandeFinancement(DossierClient dossierClient,
                              Long commercantUserId,
                              String referenceDemande,
                              BigDecimal montant,
                              Integer dureeMois,
                              String statut,
                              LocalDateTime dateCreation,
                              LocalDateTime dateDerniereMiseAJour,
                              String typeProduit) {
        this.dossierClient         = dossierClient;
        this.commercantUserId      = commercantUserId;
        this.referenceDemande      = referenceDemande;
        this.montant               = montant;
        this.dureeMois             = dureeMois;
        this.statut                = statut;
        this.dateCreation          = dateCreation;
        this.dateDerniereMiseAJour = dateDerniereMiseAJour;
        this.typeProduit           = typeProduit;
    }

    public DemandeFinancement() {}
}