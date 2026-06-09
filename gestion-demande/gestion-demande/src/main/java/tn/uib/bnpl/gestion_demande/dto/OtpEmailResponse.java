package tn.uib.bnpl.gestion_demande.dto;

public record OtpEmailResponse(
        String otp,
        String emailClient,
        String referenceDemande
) {}
