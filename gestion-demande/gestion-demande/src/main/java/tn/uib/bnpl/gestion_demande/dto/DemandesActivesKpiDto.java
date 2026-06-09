package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;

/** Snapshot KPI des demandes actives (table demande_financement). */
public record DemandesActivesKpiDto(
        long demandesActivesTotal,
        long demandesEnCoursAnalyse,
        long demandesEnAttenteComplement,
        long demandesSoumises,
        BigDecimal montantTotalActif
) {}
