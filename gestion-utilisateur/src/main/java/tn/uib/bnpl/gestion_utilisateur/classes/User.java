package tn.uib.bnpl.gestion_utilisateur.classes;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id_user;

    private String nom;
    private String prenom;

    /** Carte d'identité nationale — unique si renseigné (client / utilisateur identifié). */
    @Column(unique = true)
    private String cin;

    private String date_naissance;

    @Column(unique = true)
    private String email;

    private String telephone;
    private String adresse;
    private String ville;

    /** Pays (ex. TN) — complète {@code ville} pour l’adresse. */
    private String pays;

    /** Nationalité (libellé ou code). */
    private String nationalite;

    /** Sexe : M, F, ou libellé selon votre convention front. */
    private String sexe;

    /** Profession (utile dossier crédit / client BNPL). */
    private String profession;

    /** Employeur (client salarié). */
    private String employeur;

    /** Absent pour les profils {@code CLIENT} (pas de login mot de passe). */
    @Column(nullable = true)
    private String password;

    /** Rôle métier : ADMIN, COMMERCANT, BANQUE, CLIENT, … */
    private String role;

    /**
     * ICE — Identifiant Commun de l’Entreprise (Tunisie), pour les profils entreprise / commerçant.
     */
    @Column(length = 64)
    private String ice;

    private String nom_magasin;

    private String nom_banque;
    private String code_banque;

    private Boolean statut = true;

    private LocalDateTime date_creation = LocalDateTime.now();

    /** Dernière modification du profil (à mettre à jour dans le service lors des save). */
    private LocalDateTime date_modification;

    /** Requis par JPA. */
    public User() {}

    /**
     * <strong>Client</strong> (emprunteur) — rôle {@code CLIENT}.
     * Caractérisé par : nom, prénom, CIN, email, adresse, sexe, profession, employeur.
     * <p>Sans mot de passe : le client ne se connecte pas comme un commerçant ; le champ {@code password}
     * est renseigné ailleurs si besoin (ex. mot de passe aléatoire + lien, ou {@link #setPassword} avant {@code save}).
     */
    public User(String nom, String prenom, String cin, String email, String adresse,
                String sexe, String profession, String employeur) {
        this.nom = nom;
        this.prenom = prenom;
        this.cin = cin;
        this.email = email;
        this.adresse = adresse;
        this.sexe = sexe;
        this.profession = profession;
        this.employeur = employeur;
        this.role = "CLIENT";
        this.statut = true;
        this.date_creation = LocalDateTime.now();
    }

    /**
     * <strong>Commerçant</strong> — rôle {@code COMMERCANT}.
     * Caractérisé par : nom du magasin, ICE, adresse, email, mot de passe.
     * (Pas de conflit de signature avec la banque : 5 paramètres ici, 4 pour la banque.)
     */
    public User(String nomMagasin, String ice, String adresse, String email, String password) {
        this.nom_magasin = nomMagasin;
        this.ice = ice;
        this.adresse = adresse;
        this.email = email;
        this.password = password;
        this.role = "COMMERCANT";
        this.statut = true;
        this.date_creation = LocalDateTime.now();
    }

    /**
     * <strong>Banque</strong> (compte lié à un établissement) — rôle {@code BANQUE}.
     * Caractérisé par : nom de la banque, code banque, email, adresse.
     * Le mot de passe n’est pas dans ce constructeur (4 paramètres) : appeler {@link #setPassword(String)}
     * avec le hash BCrypt avant {@code save}, ou compléter nom/prénom du contact si besoin via les setters.
     */
    public User(String nomBanque, String codeBanque, String email, String adresse) {
        this.nom_banque = nomBanque;
        this.code_banque = codeBanque;
        this.email = email;
        this.adresse = adresse;
        this.role = "BANQUE";
        this.statut = true;
        this.date_creation = LocalDateTime.now();
    }

    // getters et setters

    @JsonProperty("id")
    public Long getId_user() {
        return id_user;
    }

    @JsonProperty("id")
    public void setId_user(Long id_user) {
        this.id_user = id_user;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public String getCin() {
        return cin;
    }

    public void setCin(String cin) {
        this.cin = cin;
    }

    public String getDate_naissance() {
        return date_naissance;
    }

    public void setDate_naissance(String date_naissance) {
        this.date_naissance = date_naissance;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getVille() {
        return ville;
    }

    public void setVille(String ville) {
        this.ville = ville;
    }

    public String getPays() {
        return pays;
    }

    public void setPays(String pays) {
        this.pays = pays;
    }

    public String getNationalite() {
        return nationalite;
    }

    public void setNationalite(String nationalite) {
        this.nationalite = nationalite;
    }

    public String getSexe() {
        return sexe;
    }

    public void setSexe(String sexe) {
        this.sexe = sexe;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getEmployeur() {
        return employeur;
    }

    public void setEmployeur(String employeur) {
        this.employeur = employeur;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getNom_magasin() {
        return nom_magasin;
    }

    public void setNom_magasin(String nom_magasin) {
        this.nom_magasin = nom_magasin;
    }

    public String getIce() {
        return ice;
    }

    public void setIce(String ice) {
        this.ice = ice;
    }

    public String getNom_banque() {
        return nom_banque;
    }

    public void setNom_banque(String nom_banque) {
        this.nom_banque = nom_banque;
    }

    public String getCode_banque() {
        return code_banque;
    }

    public void setCode_banque(String code_banque) {
        this.code_banque = code_banque;
    }

    public Boolean getStatut() {
        return statut;
    }

    public void setStatut(Boolean statut) {
        this.statut = statut;
    }

    public LocalDateTime getDate_creation() {
        return date_creation;
    }

    public void setDate_creation(LocalDateTime date_creation) {
        this.date_creation = date_creation;
    }

    public LocalDateTime getDate_modification() {
        return date_modification;
    }

    public void setDate_modification(LocalDateTime date_modification) {
        this.date_modification = date_modification;
    }
}
