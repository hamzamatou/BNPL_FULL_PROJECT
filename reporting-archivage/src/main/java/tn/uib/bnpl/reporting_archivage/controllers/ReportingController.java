package tn.uib.bnpl.reporting_archivage.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.reporting_archivage.classes.*;
import tn.uib.bnpl.reporting_archivage.config.SecurityUtils;
import tn.uib.bnpl.reporting_archivage.dto.BanqueDashboardDto;
import tn.uib.bnpl.reporting_archivage.dto.DashboardReportingDto;
import tn.uib.bnpl.reporting_archivage.services.ReportingService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/reporting")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ANALYSTE_BANCAIRE')")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('ADMIN')")
    public DashboardReportingDto dashboard() {
        return reportingService.getDashboard();
    }

    @GetMapping("/dashboard/banque")
    @PreAuthorize("hasAnyAuthority('ANALYSTE_BANCAIRE', 'BANQUE')")
    public BanqueDashboardDto dashboardBanque() {
        return reportingService.getDashboardBanque(SecurityUtils.getCurrentUserId());
    }

    @GetMapping("/actions-demandes")
    public Page<ActionDemandeHistorique> actionsDemandes(
            @RequestParam(required = false) Long demandeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportingService.getActionsDemandes(demandeId, type, debut, fin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateAction")));
    }

    @GetMapping("/actions-documents")
    public Page<ActionDocumentHistorique> actionsDocuments(
            @RequestParam(required = false) Long demandeId,
            @RequestParam(required = false) String objectKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportingService.getActionsDocuments(demandeId, objectKey, debut, fin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateAction")));
    }

    @GetMapping("/acces")
    @PreAuthorize("hasAuthority('ADMIN')")
    public Page<AccesPlateformeHistorique> acces(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean suspectOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportingService.getAcces(userId, suspectOnly, debut, fin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateAcces")));
    }

    @GetMapping("/decisions")
    public Page<DecisionFinancementHistorique> decisions(
            @RequestParam(required = false) Long demandeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(required = false) Long acteurUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long effectiveActeurId = resolveActeurUserId(acteurUserId);
        return reportingService.getDecisions(demandeId, type, debut, fin, effectiveActeurId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateDecision")));
    }

    private Long resolveActeurUserId(Long requested) {
        if (SecurityUtils.isAnalysteBanque()) {
            return SecurityUtils.getCurrentUserId();
        }
        return requested;
    }
}
