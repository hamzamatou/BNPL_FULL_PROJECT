package tn.uib.bnpl.gestion_demande.dto;

public record GenerateOtpEmailRequest(
        String token,
        String nom,
        String prenom,
        String cin
) {}
