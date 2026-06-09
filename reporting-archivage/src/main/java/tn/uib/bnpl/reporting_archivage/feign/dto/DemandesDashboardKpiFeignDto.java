package tn.uib.bnpl.reporting_archivage.feign.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DemandesDashboardKpiFeignDto(
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
