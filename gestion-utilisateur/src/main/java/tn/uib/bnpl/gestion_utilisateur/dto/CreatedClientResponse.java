package tn.uib.bnpl.gestion_utilisateur.dto;

/**
 * Retour après création d'un utilisateur rôle CLIENT (dossier BNPL).
 */
public record CreatedClientResponse(Long id, String email) {}
