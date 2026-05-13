package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DemandeCompleteResponse(
        Long id,
        String referenceDemande,
        BigDecimal montant,
        Integer dureeMois,
        String statut,
        LocalDateTime dateCreation,
        LocalDateTime dateDerniereMiseAJour,
        String typeProduit,
        ClientIdentityDto client,
        DossierClientResponse dossierClient
) {}
