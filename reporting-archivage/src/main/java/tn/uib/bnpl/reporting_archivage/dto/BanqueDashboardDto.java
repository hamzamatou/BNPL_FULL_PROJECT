package tn.uib.bnpl.reporting_archivage.dto;

import java.util.List;
import java.util.Map;

/** Tableau de bord analyste banque — périmètre limité à l'utilisateur connecté. */
public record BanqueDashboardDto(
        long decisions24h,
        long acceptees,
        long refusees,
        long complements,
        long prisesEnCharge,
        Map<String, Long> repartitionDecisionsParType,
        List<DecisionResumeDto> dernieresDecisions
) {
    public record DecisionResumeDto(
            Long id,
            Long demandeId,
            String referenceDemande,
            String typeDecision,
            String libelle,
            java.time.LocalDateTime dateDecision
    ) {}
}
