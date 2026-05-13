package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DossierClientResponse(
        Long id,
        Long clientId,
        String referenceDossier,
        LocalDateTime dateCreation,
        LocalDateTime dateDerniereMiseAJour,
        String situationFamiliale,
        Integer nombreEnfants,
        Integer ancienneteEmploiMois,
        String typeContrat,
        BigDecimal revenuMensuelNet,
        BigDecimal autresRevenusMensuels,
        BigDecimal revenuAnnuel,
        BigDecimal loyerMensuel,
        BigDecimal mensualitesCredits,
        BigDecimal autresChargesFixes,
        BigDecimal chargesMensuelles,
        BigDecimal encoursCredits,
        BigDecimal tauxEndettement,
        List<DocumentDossierResponse> documents
) {}
