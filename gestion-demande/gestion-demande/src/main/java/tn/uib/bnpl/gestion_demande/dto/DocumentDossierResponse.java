package tn.uib.bnpl.gestion_demande.dto;

public record DocumentDossierResponse(
        Long id,
        String typeDocument,
        String objectKey,
        String nomFichier,
        String contentType,
        Long tailleOctets
) {}
