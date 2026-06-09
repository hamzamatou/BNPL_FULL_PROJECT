package tn.uib.bnpl.reporting_archivage.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminDashboardKpiSnapshot(
        long demandesTotal,
        long demandesCeMois,
        BigDecimal montantTotalDemande,
        BigDecimal montantMoyenDemande,
        long clientsInscrits,
        long commercantsPartenaires,
        long banquesPartenaires,
        long utilisateursActifs,
        long utilisateursTotal,
        long demandesAcceptees,
        long demandesRefusees,
        double tauxAcceptationPct,
        long demandesEnCoursAnalyse,
        long demandesCloturees,
        double scoreMoyenPrescoring,
        long prescoringRisqueFaible,
        long prescoringRisqueMoyen,
        long prescoringRisqueEleve,
        long demandesRoutees,
        long reponsesBancairesRecues,
        Double tempsMoyenTraitementHeures,
        Map<String, Long> repartitionPrescoringParZone,
        Map<String, Long> evolutionDemandesParJour,
        Map<String, Long> repartitionStatuts,
        Map<String, Double> tauxAcceptationParBanque,
        Map<String, Long> demandesParCommercant
) {}
