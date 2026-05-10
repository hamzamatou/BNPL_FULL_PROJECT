package tn.uib.bnpl.gestion_utilisateur.dto;

/**
 * Données d'identité exposées à gestion-demande (OTP / vérifications).
 */
public record ClientIdentityResponse(
        Long id,
        String nom,
        String prenom,
        String cin,
        String telephone,
        String email
) {}
