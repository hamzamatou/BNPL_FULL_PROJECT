package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;

/**
 * Pré-remplissage des champs de "données financières" à partir du dernier dossier du client.
 */
public record DernierDossierFinancierResponse(
        Integer ancienneteEmploiMois,
        BigDecimal revenuMensuelNet,
        BigDecimal autresRevenusMensuels,
        BigDecimal loyerMensuel,
        BigDecimal mensualitesCredits,
        BigDecimal autresChargesFixes,
        BigDecimal encoursCredits
) {}

