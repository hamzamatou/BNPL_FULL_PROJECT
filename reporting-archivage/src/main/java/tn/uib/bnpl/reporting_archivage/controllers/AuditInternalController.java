package tn.uib.bnpl.reporting_archivage.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;
import tn.uib.bnpl.reporting_archivage.services.AuditEventService;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/audit")
public class AuditInternalController {

    private final AuditEventService auditEventService;

    public AuditInternalController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recevoirEvenement(@Valid @RequestBody AuditEventRequest request) {
        auditEventService.traiterEvenement(request);
        return Map.of("status", "ACCEPTED", "eventType", request.eventType());
    }
}
