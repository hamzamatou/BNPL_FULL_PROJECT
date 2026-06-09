package tn.uib.bnpl.gestion_demande.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Client HTTP vers {@code gestion-utilisateur} (appels inter-services).
 * URL : {@code gestion-utilisateur.url} (port 8080 par défaut).
 */
@FeignClient(
        name = "gestion-utilisateur",
        url = "${gestion-utilisateur.url:http://localhost:8080}",
        configuration = FeignClientConfig.class
)
public interface ClientUtilisateurFeign {

    /** Création d’un client final (emprunteur) via endpoint interne sécurisé par clé API. */
    @PostMapping("/api/internal/clients")
    ClientResponse creerClient(@RequestBody ClientCreationRequest request);

    /** Mise à jour d'un client existant via endpoint interne sécurisé par clé API. */
    @PutMapping("/api/internal/clients/{id}")
    ClientResponse modifierClient(@PathVariable("id") Long id, @RequestBody ClientCreationRequest request);

    /** Récupération identité client via endpoint interne sécurisé par clé API. */
    @GetMapping("/api/internal/clients/{id}/identity")
    ClientIdentityResponse getClientIdentity(@PathVariable("id") Long id);

    /** Récupération id client via CIN (endpoint interne). */
    @GetMapping("/api/internal/clients/by-cin")
    ClientIdResponse getClientIdByCin(@RequestParam("cin") String cin);

    /**
     * Analystes actifs d'une banque par {@code codeBanque} (aligné sur {@code banquesRoutees}).
     */
    @GetMapping("/api/internal/banques/analystes")
    List<AnalysteRoutageResponse> listerAnalystesActifsParCodeBanque(@RequestParam("codeBanque") String codeBanque);

    /** DTO pour la création client */
    class ClientCreationRequest {
        private String nom;
        private String prenom;
        private String email;
        private String telephone;
        private String cin;
        private String adresse;
        private String sexe;
        private String profession;
        private String employeur;
        private String dateNaissance;

        // Getters / Setters
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
        public String getDateNaissance() { return dateNaissance; }
        public void setDateNaissance(String dateNaissance) { this.dateNaissance = dateNaissance; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true) 
    /** DTO pour la réponse après création client */
    class ClientResponse {
        private Long id;
        private String email;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    /** DTO pour identité client */
    class ClientIdentityResponse {
        private Long id;
        private String nom;
        private String prenom;
        private String cin;
        private String telephone;
        private String email;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }
        public String getCin() { return cin; }
        public void setCin(String cin) { this.cin = cin; }
        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    /** DTO minimal pour ne récupérer que l'id du client. */
    class ClientIdResponse {
        private Long id;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class AnalysteRoutageResponse {
        private Long id;
        private String email;
        private String nom;
        private String prenom;
        private Long banqueId;
        private String codeBanque;
        private String nomBanque;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }
        public Long getBanqueId() { return banqueId; }
        public void setBanqueId(Long banqueId) { this.banqueId = banqueId; }
        public String getCodeBanque() { return codeBanque; }
        public void setCodeBanque(String codeBanque) { this.codeBanque = codeBanque; }
        public String getNomBanque() { return nomBanque; }
        public void setNomBanque(String nomBanque) { this.nomBanque = nomBanque; }
    }
}