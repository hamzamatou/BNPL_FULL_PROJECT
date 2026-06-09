package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;
import java.util.Map;

/** KPI dashboard admin — source : table {@code demande_financement}. */
public record DemandesDashboardKpiDto(
        long demandesActivesTotal,
        long demandesCeMois,
        long demandesEnCoursAnalyse,
        long demandesEnAttenteComplement,
        long demandesSoumises,
        long demandesAcceptees,
        long demandesRefusees,
        BigDecimal montantTotalActif,
        Map<String, Long> repartitionStatuts,
        Map<String, Long> evolutionCreationsParJour,
        Map<String, Long> demandesParCommercantUserId
) {}
