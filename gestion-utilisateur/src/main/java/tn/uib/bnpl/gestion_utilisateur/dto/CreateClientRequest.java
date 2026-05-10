package tn.uib.bnpl.gestion_utilisateur.dto;

/**
 * Création d'un client final (emprunteur) depuis gestion-demande.
 * Aligné sur le constructeur {@code User(nom, prenom, cin, email, adresse, sexe, profession, employeur)}
 * + {@code telephone} (hors constructeur).
 */
public record CreateClientRequest(
        String nom,
        String prenom,
        String email,
        String telephone,
        String cin,
        String adresse,
        String sexe,
        String profession,
        String employeur
) {}
