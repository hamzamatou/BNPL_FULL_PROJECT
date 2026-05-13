package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class DemandeFinancement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dossier auquel appartient la demande
    @ManyToOne(optional = false)
    private DossierClient dossierClient;

    // Id du user commerçant (récupéré depuis le JWT)
    @Column(nullable = false)
    private Long commercantUserId;

    @Column(nullable = false, unique = true)
    private String referenceDemande; // ex : DEM-2026-000045

    @Column(nullable = false)
    private BigDecimal montant;

    @Column(nullable = false)
    private Integer dureeMois;

    @Column(nullable = false)
    private String statut; // BROUILLON, SOUMISE, EN_COURS, ACCEPTEE, REFUSEE...

    private LocalDateTime dateCreation;
    private LocalDateTime dateDerniereMiseAJour;
    private String typeProduit;        // ex : ELECTRONIQUE, ELECTROMENAGER...

    /** Une ligne par banque : routage, verrou, expiration et décision (fusion affectation + traitement). */
    @OneToMany(mappedBy = "demande", fetch = FetchType.LAZY)
    private List<PriseEnCharge> prisesEnCharge;

    // --- Getters / Setters ---

    public Long getId() {
        return id;
    }

    public DossierClient getDossierClient() {
        return dossierClient;
    }

    public void setDossierClient(DossierClient dossierClient) {
        this.dossierClient = dossierClient;
    }

    public Long getCommercantUserId() {
        return commercantUserId;
    }

    public void setCommercantUserId(Long commercantUserId) {
        this.commercantUserId = commercantUserId;
    }

    public String getReferenceDemande() {
        return referenceDemande;
    }

    public void setReferenceDemande(String referenceDemande) {
        this.referenceDemande = referenceDemande;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public Integer getDureeMois() {
        return dureeMois;
    }

    public void setDureeMois(Integer dureeMois) {
        this.dureeMois = dureeMois;
    }

    public String getStatut() {
        return statut;
    }

    public void setStatut(String statut) {
        this.statut = statut;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateDerniereMiseAJour() {
        return dateDerniereMiseAJour;
    }

    public void setDateDerniereMiseAJour(LocalDateTime dateDerniereMiseAJour) {
        this.dateDerniereMiseAJour = dateDerniereMiseAJour;
    }

    public String getTypeProduit() {
        return typeProduit;
    }

    public void setTypeProduit(String typeProduit) {
        this.typeProduit = typeProduit;
    }

    public List<PriseEnCharge> getPrisesEnCharge() {
        return prisesEnCharge;
    }

    public void setPrisesEnCharge(List<PriseEnCharge> prisesEnCharge) {
        this.prisesEnCharge = prisesEnCharge;
    }

    // --- Constructeurs ---

    public DemandeFinancement(DossierClient dossierClient,
                                Long commercantUserId,
                                String referenceDemande,
                                BigDecimal montant,
                                Integer dureeMois,
                                String statut,
                                LocalDateTime dateCreation,
                                LocalDateTime dateDerniereMiseAJour,
                                String typeProduit) {
        this.dossierClient = dossierClient;
        this.commercantUserId = commercantUserId;
        this.referenceDemande = referenceDemande;
        this.montant = montant;
        this.dureeMois = dureeMois;
        this.statut = statut;
        this.dateCreation = dateCreation;
        this.dateDerniereMiseAJour = dateDerniereMiseAJour;
        this.typeProduit = typeProduit;
    }

    public DemandeFinancement() {
        // JPA
    }
}