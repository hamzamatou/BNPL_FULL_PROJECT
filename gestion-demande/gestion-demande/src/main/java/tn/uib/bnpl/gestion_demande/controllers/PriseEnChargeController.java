package tn.uib.bnpl.gestion_demande.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.dto.DemandeCompleteResponse;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;
import tn.uib.bnpl.gestion_demande.services.PriseEnChargeService;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import java.util.List;

/**
 * Point d'entrée HTTP unique pour la prise en charge banque (listes, détail sous verrou, décisions).
 */
@RestController
@RequestMapping("/api/prises-en-charge")
public class PriseEnChargeController {

    private final PriseEnChargeService priseEnChargeService;
    private final DemandeDtoMapper demandeDtoMapper;

    public PriseEnChargeController(
            PriseEnChargeService priseEnChargeService,
            DemandeDtoMapper demandeDtoMapper) {
        this.priseEnChargeService = priseEnChargeService;
        this.demandeDtoMapper = demandeDtoMapper;
    }

    @GetMapping("/demandes/disponibles")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesDisponibles() {
        return ResponseEntity.ok(
                priseEnChargeService.listerDemandesDisponiblesPourBanque().stream()
                        .map(demandeDtoMapper::toSummary)
                        .toList()
        );
    }

    @GetMapping("/demandes/verrouillees")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesVerrouillees() {
        return ResponseEntity.ok(
                priseEnChargeService.listerDemandesVerrouilleesPourBanque().stream()
                        .map(demandeDtoMapper::toSummary)
                        .toList()
        );
    }

    @GetMapping("/demandes/{demandeId}/detail")
    public ResponseEntity<DemandeCompleteResponse> getDetailSousVerrou(@PathVariable Long demandeId) {
        return ResponseEntity.ok(
                demandeDtoMapper.toComplete(
                        priseEnChargeService.getDemandeDetailPourBanqueVerrouillee(demandeId))
        );
    }

    @PostMapping("/demandes/{demandeId}/se-saisir")
    public ResponseEntity<PriseEnCharge> seSaisir(@PathVariable Long demandeId) {
        PriseEnCharge pec = priseEnChargeService.seSaisirEtDemarrerAnalyse(demandeId);
        return ResponseEntity.status(HttpStatus.CREATED).body(pec);
    }

    @PostMapping("/demandes/{demandeId}/accepter")
    public ResponseEntity<DemandeCompleteResponse> accepter(
            @PathVariable Long demandeId,
            @RequestBody(required = false) DecisionRequest req
    ) {
        String commentaire = req != null ? req.commentaire() : null;
        return ResponseEntity.ok(
                demandeDtoMapper.toComplete(
                        priseEnChargeService.accepterDemande(demandeId, commentaire))
        );
    }

    @PostMapping("/demandes/{demandeId}/refuser")
    public ResponseEntity<DemandeCompleteResponse> refuser(
            @PathVariable Long demandeId,
            @RequestBody(required = false) DecisionRequest req
    ) {
        String motifRefus = req != null ? req.motifRefus() : null;
        String commentaire = req != null ? req.commentaire() : null;
        return ResponseEntity.ok(
                demandeDtoMapper.toComplete(
                        priseEnChargeService.refuserDemande(demandeId, motifRefus, commentaire))
        );
    }

    @PostMapping("/demandes/{demandeId}/complements")
    public ResponseEntity<DemandeCompleteResponse> complements(
            @PathVariable Long demandeId,
            @RequestBody(required = false) DecisionRequest req
    ) {
        String commentaire = req != null ? req.commentaire() : null;
        return ResponseEntity.ok(
                demandeDtoMapper.toComplete(
                        priseEnChargeService.demanderComplements(demandeId, commentaire))
        );
    }

    public record DecisionRequest(String motifRefus, String commentaire) {}
}
