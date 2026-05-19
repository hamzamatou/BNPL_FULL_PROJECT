package tn.uib.bnpl.gestion_demande.dto;
 
import java.math.BigDecimal;
 
public record DernierDossierFinancierResponse(
        Integer    ancienneteEmploiMois,
        BigDecimal revenuMensuelNet,
        BigDecimal autresRevenusMensuels,
        BigDecimal loyerMensuel,
        BigDecimal mensualitesCredits,
        BigDecimal autresChargesFixes,
        BigDecimal encoursCredits
) {}