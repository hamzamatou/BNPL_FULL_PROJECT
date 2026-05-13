package tn.uib.bnpl.gestion_demande.dto;

import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CreationDemandeCompleteRequest {

    // Client
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String cin;
    private String adresse;
    private String sexe;
    private String profession;
    private String employeur;
    /** Date de naissance client utilisée pour création dans gestion-utilisateur. */
    private LocalDate dateNaissance;
    private String typeContrat;

    // Dossier
    private String situationFamiliale;
    private Integer nombreEnfants;
    private Integer ancienneteEmploiMois;
    private BigDecimal revenuMensuelNet;
    private BigDecimal autresRevenusMensuels;
    private BigDecimal revenuAnnuel;
    private BigDecimal encoursCredits;

    // Charges séparées
    private BigDecimal loyerMensuel;
    private BigDecimal mensualitesCredits;
    private BigDecimal autresChargesFixes;

    // Demande
    private BigDecimal montant;
    private Integer dureeMois;
    private String typeProduit;

    // Documents (chaque doc contient typeDocument + fichier binaire)
    private List<DocumentMultipart> documents = new ArrayList<>();

    public static class DocumentMultipart {
        private String typeDocument;
        private MultipartFile file; // <-- binaire

        public String getTypeDocument() { return typeDocument; }
        public void setTypeDocument(String typeDocument) { this.typeDocument = typeDocument; }

        public MultipartFile getFile() { return file; }
        public void setFile(MultipartFile file) { this.file = file; }
    }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public String getProfession() { return profession; }
    public void setProfession(String profession) { this.profession = profession; }

    public String getEmployeur() { return employeur; }
    public void setEmployeur(String employeur) { this.employeur = employeur; }

    public LocalDate getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getTypeContrat() { return typeContrat; }
    public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }

    public String getSituationFamiliale() { return situationFamiliale; }
    public void setSituationFamiliale(String situationFamiliale) { this.situationFamiliale = situationFamiliale; }

    public Integer getNombreEnfants() { return nombreEnfants; }
    public void setNombreEnfants(Integer nombreEnfants) { this.nombreEnfants = nombreEnfants; }

    public Integer getAncienneteEmploiMois() { return ancienneteEmploiMois; }
    public void setAncienneteEmploiMois(Integer ancienneteEmploiMois) { this.ancienneteEmploiMois = ancienneteEmploiMois; }

    public BigDecimal getRevenuMensuelNet() { return revenuMensuelNet; }
    public void setRevenuMensuelNet(BigDecimal revenuMensuelNet) { this.revenuMensuelNet = revenuMensuelNet; }

    public BigDecimal getAutresRevenusMensuels() { return autresRevenusMensuels; }
    public void setAutresRevenusMensuels(BigDecimal autresRevenusMensuels) { this.autresRevenusMensuels = autresRevenusMensuels; }

    public BigDecimal getRevenuAnnuel() { return revenuAnnuel; }
    public void setRevenuAnnuel(BigDecimal revenuAnnuel) { this.revenuAnnuel = revenuAnnuel; }

    public BigDecimal getEncoursCredits() { return encoursCredits; }
    public void setEncoursCredits(BigDecimal encoursCredits) { this.encoursCredits = encoursCredits; }

    public BigDecimal getLoyerMensuel() { return loyerMensuel; }
    public void setLoyerMensuel(BigDecimal loyerMensuel) { this.loyerMensuel = loyerMensuel; }

    public BigDecimal getMensualitesCredits() { return mensualitesCredits; }
    public void setMensualitesCredits(BigDecimal mensualitesCredits) { this.mensualitesCredits = mensualitesCredits; }

    public BigDecimal getAutresChargesFixes() { return autresChargesFixes; }
    public void setAutresChargesFixes(BigDecimal autresChargesFixes) { this.autresChargesFixes = autresChargesFixes; }

    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }

    public Integer getDureeMois() { return dureeMois; }
    public void setDureeMois(Integer dureeMois) { this.dureeMois = dureeMois; }

    public String getTypeProduit() { return typeProduit; }
    public void setTypeProduit(String typeProduit) { this.typeProduit = typeProduit; }

    public List<DocumentMultipart> getDocuments() { return documents; }
    public void setDocuments(List<DocumentMultipart> documents) { this.documents = documents; }
}