package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class DossierClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long clientId;

    @Column(nullable = false, unique = true)
    private String referenceDossier;

    @Column(nullable = false)
    private LocalDateTime dateCreation;

    private LocalDateTime dateDerniereMiseAJour;

    private Integer ancienneteEmploiMois;
    private String typeContrat;

    // Revenu
    private BigDecimal revenuMensuelNet;
    private BigDecimal autresRevenusMensuels; // optionnel
    private BigDecimal revenuAnnuel;

    // Charges saisies séparées (et calculées ensuite)
    private BigDecimal loyerMensuel;          // separer
    private BigDecimal mensualitesCredits;   // mensualites crédits existants
    private BigDecimal autresChargesFixes;   // autres charges fixes

    // Calculé (stocké)
    private BigDecimal chargesMensuelles;    // = loyer + mensualites crédits + autres fixes + enfants (charges vie, hors déf. BCT)
    private BigDecimal encoursCredits;       // optionnel (utile pour l'info / IA)
    /** Taux type BCT : (mensualités crédits existants + mensualité du financement demandé) / revenus nets mensuels. */
    private BigDecimal tauxEndettement;

    /** Ex. CELIBATAIRE, MARIE, PACSE, DIVORCE, VEUF, CONCUBINAGE */
    private String situationFamiliale;

    private Integer nombreEnfants;

    @OneToMany(mappedBy = "dossierClient")
    private List<DemandeFinancement> demandes;

    @OneToMany(mappedBy = "dossierClient")
    private List<DocumentDossier> documents;
    public DossierClient() {
        // JPA
    }
    public DossierClient(Long clientId,
                          String referenceDossier,
                          LocalDateTime dateCreation,
                          LocalDateTime dateDerniereMiseAJour,
                          Integer ancienneteEmploiMois,
                          String typeContrat,
                          BigDecimal revenuMensuelNet,
                          BigDecimal autresRevenusMensuels,
                          BigDecimal revenuAnnuel,
                          BigDecimal encoursCredits,
                          BigDecimal loyerMensuel,
                          BigDecimal mensualitesCredits,
                          BigDecimal autresChargesFixes,
                          BigDecimal chargesMensuelles,
                          BigDecimal tauxEndettement,
                          String situationFamiliale,
                          Integer nombreEnfants) {
        this.clientId = clientId;
        this.referenceDossier = referenceDossier;
        this.dateCreation = dateCreation;
        this.dateDerniereMiseAJour = dateDerniereMiseAJour;
        this.ancienneteEmploiMois = ancienneteEmploiMois;
        this.typeContrat = typeContrat;
        this.revenuMensuelNet = revenuMensuelNet;
        this.autresRevenusMensuels = autresRevenusMensuels;
        this.revenuAnnuel = revenuAnnuel;
        this.encoursCredits = encoursCredits;
        this.loyerMensuel = loyerMensuel;
        this.mensualitesCredits = mensualitesCredits;
        this.autresChargesFixes = autresChargesFixes;
        this.chargesMensuelles = chargesMensuelles;
        this.tauxEndettement = tauxEndettement;
        this.situationFamiliale = situationFamiliale;
        this.nombreEnfants = nombreEnfants;
    }
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Long getClientId() {
		return clientId;
	}
	public void setClientId(Long clientId) {
		this.clientId = clientId;
	}
	public String getReferenceDossier() {
		return referenceDossier;
	}
	public void setReferenceDossier(String referenceDossier) {
		this.referenceDossier = referenceDossier;
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
	public Integer getAncienneteEmploiMois() {
		return ancienneteEmploiMois;
	}
	public void setAncienneteEmploiMois(Integer ancienneteEmploiMois) {
		this.ancienneteEmploiMois = ancienneteEmploiMois;
	}
	public String getTypeContrat() {
		return typeContrat;
	}
	public void setTypeContrat(String typeContrat) {
		this.typeContrat = typeContrat;
	}
	public BigDecimal getRevenuMensuelNet() {
		return revenuMensuelNet;
	}
	public void setRevenuMensuelNet(BigDecimal revenuMensuelNet) {
		this.revenuMensuelNet = revenuMensuelNet;
	}
	public BigDecimal getAutresRevenusMensuels() {
		return autresRevenusMensuels;
	}
	public void setAutresRevenusMensuels(BigDecimal autresRevenusMensuels) {
		this.autresRevenusMensuels = autresRevenusMensuels;
	}
	public BigDecimal getRevenuAnnuel() {
		return revenuAnnuel;
	}
	public void setRevenuAnnuel(BigDecimal revenuAnnuel) {
		this.revenuAnnuel = revenuAnnuel;
	}
	public BigDecimal getLoyerMensuel() {
		return loyerMensuel;
	}
	public void setLoyerMensuel(BigDecimal loyerMensuel) {
		this.loyerMensuel = loyerMensuel;
	}
	public BigDecimal getMensualitesCredits() {
		return mensualitesCredits;
	}
	public void setMensualitesCredits(BigDecimal mensualitesCredits) {
		this.mensualitesCredits = mensualitesCredits;
	}
	public BigDecimal getAutresChargesFixes() {
		return autresChargesFixes;
	}
	public void setAutresChargesFixes(BigDecimal autresChargesFixes) {
		this.autresChargesFixes = autresChargesFixes;
	}
	public BigDecimal getChargesMensuelles() {
		return chargesMensuelles;
	}
	public void setChargesMensuelles(BigDecimal chargesMensuelles) {
		this.chargesMensuelles = chargesMensuelles;
	}
	public BigDecimal getEncoursCredits() {
		return encoursCredits;
	}
	public void setEncoursCredits(BigDecimal encoursCredits) {
		this.encoursCredits = encoursCredits;
	}
	public BigDecimal getTauxEndettement() {
		return tauxEndettement;
	}
	public void setTauxEndettement(BigDecimal tauxEndettement) {
		this.tauxEndettement = tauxEndettement;
	}
	public String getSituationFamiliale() {
		return situationFamiliale;
	}
	public void setSituationFamiliale(String situationFamiliale) {
		this.situationFamiliale = situationFamiliale;
	}
	public Integer getNombreEnfants() {
		return nombreEnfants;
	}
	public void setNombreEnfants(Integer nombreEnfants) {
		this.nombreEnfants = nombreEnfants;
	}
	public List<DemandeFinancement> getDemandes() {
		return demandes;
	}
	public void setDemandes(List<DemandeFinancement> demandes) {
		this.demandes = demandes;
	}
	public List<DocumentDossier> getDocuments() {
		return documents;
	}
	public void setDocuments(List<DocumentDossier> documents) {
		this.documents = documents;
	}

    // Getters / Setters (avec IDE)
}