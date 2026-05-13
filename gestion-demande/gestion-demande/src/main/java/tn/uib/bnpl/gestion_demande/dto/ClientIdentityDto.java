package tn.uib.bnpl.gestion_demande.dto;

public record ClientIdentityDto(
        Long id,
        String nom,
        String prenom,
        String cin,
        String telephone,
        String email
) {}