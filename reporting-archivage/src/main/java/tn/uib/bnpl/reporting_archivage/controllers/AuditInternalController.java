package tn.uib.bnpl.reporting_archivage.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.reporting_archivage.dto.ActionDemandeHistoriqueViewDto;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;
import tn.uib.bnpl.reporting_archivage.services.ActionDemandeHistoriqueQueryService;
import tn.uib.bnpl.reporting_archivage.services.AuditEventService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal/audit")
public class AuditInternalController {

    private final AuditEventService auditEventService;
    private final ActionDemandeHistoriqueQueryService actionDemandeHistoriqueQueryService;

    public AuditInternalController(
            AuditEventService auditEventService,
            ActionDemandeHistoriqueQueryService actionDemandeHistoriqueQueryService) {
        this.auditEventService = auditEventService;
        this.actionDemandeHistoriqueQueryService = actionDemandeHistoriqueQueryService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recevoirEvenement(@Valid @RequestBody AuditEventRequest request) {
        auditEventService.traiterEvenement(request);
        return Map.of("status", "ACCEPTED", "eventType", request.eventType());
    }

    @GetMapping("/demandes/{demandeId}/actions")
    public List<ActionDemandeHistoriqueViewDto> listerActionsDemande(@PathVariable Long demandeId) {
        return actionDemandeHistoriqueQueryService.listerParDemande(demandeId);
    }
}
