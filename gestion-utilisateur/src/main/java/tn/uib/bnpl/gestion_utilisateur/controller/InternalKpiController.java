package tn.uib.bnpl.gestion_utilisateur.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.uib.bnpl.gestion_utilisateur.dto.ReferentielKpiDto;
import tn.uib.bnpl.gestion_utilisateur.services.ReferentielKpiService;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/kpi")
public class InternalKpiController {

    private final ReferentielKpiService referentielKpiService;

    public InternalKpiController(ReferentielKpiService referentielKpiService) {
        this.referentielKpiService = referentielKpiService;
    }

    @GetMapping("/referentiel")
    public ResponseEntity<ReferentielKpiDto> referentiel() {
        return ResponseEntity.ok(referentielKpiService.snapshot());
    }

    @GetMapping("/commercants-labels")
    public ResponseEntity<Map<String, String>> commercantsLabels() {
        return ResponseEntity.ok(referentielKpiService.commercantLabels());
    }
}
