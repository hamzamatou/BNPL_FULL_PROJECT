package tn.uib.bnpl.gestion_utilisateur.dto;

/**
 * Analyste bancaire actif, exposé pour le routage inter-services (gestion-demande).
 */
public record AnalysteRoutageDto(
        Long id,
        String email,
        String nom,
        String prenom,
        Long banqueId,
        String codeBanque,
        String nomBanque
) {
}
