package tn.uib.bnpl.reporting_archivage.dto;

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
        List<ActionDemandeResumeDto> dernieresActions
) {}
