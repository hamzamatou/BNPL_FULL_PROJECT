package tn.uib.bnpl.gestion_demande.dto;

public record GenerateConsentLinkRequest(
        Long demandeId,
        String emailClient,
        String frontBaseUrl,
        String typeAction
) {}
