package tn.uib.bnpl.reporting_archivage.feign.dto;

import java.math.BigDecimal;

public record DemandesActivesKpiFeignDto(
        long demandesActivesTotal,
        long demandesEnCoursAnalyse,
        long demandesEnAttenteComplement,
        long demandesSoumises,
        BigDecimal montantTotalActif
) {}
