package tn.uib.bnpl.gestion_utilisateur.dto;

public record LoginOtpEmailResponse(
        String email,
        String otp
) {}
