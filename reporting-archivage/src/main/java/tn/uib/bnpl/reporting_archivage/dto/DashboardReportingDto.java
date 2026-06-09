package tn.uib.bnpl.reporting_archivage.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardReportingDto(
        long actionsDemandes24h,
        long decisionsFinancement24h,
        long accesSuspects24h,
        long dossiersArchivesTotal,
        long dossiersArchives30j,
        Map<String, Long> repartitionActionsParType,
        Map<String, Long> repartitionDecisionsParType,
        List<ActionDemandeResumeDto> dernieresActions,
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
