package tn.uib.bnpl.gestion_demande.dto;

public record ConsentLinkResponse(
        String consentementUrl,
        String referenceDemande,
        String token
) {}
