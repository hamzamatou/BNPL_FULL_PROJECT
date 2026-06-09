package tn.uib.bnpl.gestion_utilisateur.dto;

public record ActivationEmailResponse(
        String email,
        String tempPassword,
        String activationUrl
) {}
