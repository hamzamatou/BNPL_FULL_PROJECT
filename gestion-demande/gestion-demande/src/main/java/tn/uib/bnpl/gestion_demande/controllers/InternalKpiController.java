package tn.uib.bnpl.gestion_demande.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.uib.bnpl.gestion_demande.dto.DemandesActivesKpiDto;
import tn.uib.bnpl.gestion_demande.dto.DemandesDashboardKpiDto;
import tn.uib.bnpl.gestion_demande.services.DemandeKpiService;

import java.util.Map;

@RestController
@RequestMapping("/api/internal/kpi")
public class InternalKpiController {

    private final DemandeKpiService demandeKpiService;

    public InternalKpiController(DemandeKpiService demandeKpiService) {
        this.demandeKpiService = demandeKpiService;
    }

    @GetMapping("/demandes-actives")
    public ResponseEntity<DemandesActivesKpiDto> demandesActives() {
        return ResponseEntity.ok(demandeKpiService.snapshotDemandesActives());
    }

    @GetMapping("/repartition-statuts-actifs")
    public ResponseEntity<Map<String, Long>> repartitionStatutsActifs() {
        return ResponseEntity.ok(demandeKpiService.repartitionStatutsActifs());
    }

    @GetMapping("/dashboard-demandes")
    public ResponseEntity<DemandesDashboardKpiDto> dashboardDemandes() {
        return ResponseEntity.ok(demandeKpiService.dashboardDemandes());
    }
}
