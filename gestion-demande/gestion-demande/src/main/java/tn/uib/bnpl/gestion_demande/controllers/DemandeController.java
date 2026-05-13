package tn.uib.bnpl.gestion_demande.controllers;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.DemandeCompleteResponse;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;
import tn.uib.bnpl.gestion_demande.dto.DernierDossierFinancierResponse;
import tn.uib.bnpl.gestion_demande.services.DemandeService;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demandes")
public class DemandeController {

    private final DemandeService demandeService;
    private final DemandeDtoMapper demandeDtoMapper;

    public DemandeController(DemandeService demandeService, DemandeDtoMapper demandeDtoMapper) {
        this.demandeService = demandeService;
        this.demandeDtoMapper = demandeDtoMapper;
    }

    @PostMapping(value = "/creation-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DemandeFinancement> creerDemandeComplete(
            @ModelAttribute CreationDemandeCompleteRequest request
    ) {
    	if (request.getDocuments() == null ||
    		    request.getDocuments().isEmpty() ||
    		    request.getDocuments().stream().allMatch(doc -> doc.getFile() == null || doc.getFile().isEmpty())) {

    		    throw new IllegalArgumentException("Au moins un document valide est requis.");
    		}
        DemandeFinancement created = demandeService.creerDemandeComplete(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/consentement/confirm")
    public ResponseEntity<DemandeSummaryResponse> validerConsentement(
            @RequestParam String token
    ) {
        DemandeFinancement updated = demandeService.validerConsentementEtSoumettre(token);
        return ResponseEntity.ok(demandeDtoMapper.toSummary(updated));
    }

    @GetMapping("/par-client")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesParClient(
            @RequestParam Long clientId
    ) {
        return ResponseEntity.ok(
                demandeService.listerDemandesParClient(clientId)
                        .stream()
                        .map(demandeDtoMapper::toSummary)
                        .toList()
        );
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<DemandeCompleteResponse> getDemandeDetail(@PathVariable("id") Long id) {
        DemandeFinancement demande = demandeService.getDemandeParIdPourCommercant(id);
        return ResponseEntity.ok(demandeDtoMapper.toComplete(demande));
    }

    @GetMapping("/dossiers/dernier")
    public ResponseEntity<DernierDossierFinancierResponse> getDernierDossierFinancier(@RequestParam("cin") String cin) {
        return ResponseEntity.ok(demandeService.getDernierDossierFinancierParCin(cin));
    }

    @GetMapping("/documents/presigned")
    public ResponseEntity<Map<String, String>> getPresignedDocumentUrl(@RequestParam String objectKey) {
        return ResponseEntity.ok(
                Map.of("url", demandeService.getPresignedDocumentUrl(objectKey))
        );
    }
}
