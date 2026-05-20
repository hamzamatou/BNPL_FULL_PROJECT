package tn.uib.bnpl.gestion_demande.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;
import tn.uib.bnpl.gestion_demande.services.AnalyseIAService;
import tn.uib.bnpl.gestion_demande.services.DemandeService;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flux en deux appels :
 *
 *  POST /api/demandes/analyse-ia        → IA seulement (pas de BDD)
 *       → 200 { recommandations, corrections, alertes, scoreCoherence }
 *       → 422 { message, anomalies, corrections? }
 *
 *  POST /api/demandes/creation-complete → persiste (reco déjà calculées côté front)
 *       → 201 DemandeSummaryResponse
 */
@RestController
@RequestMapping("/api/demandes")
public class DemandeController {

    private final DemandeService   demandeService;
    private final AnalyseIAService analyseIAService;
    private final DemandeDtoMapper dtoMapper;
    private final ObjectMapper     objectMapper;

    public DemandeController(DemandeService   demandeService,
                             AnalyseIAService analyseIAService,
                             DemandeDtoMapper dtoMapper,
                             ObjectMapper     objectMapper) {
        this.demandeService   = demandeService;
        this.analyseIAService = analyseIAService;
        this.dtoMapper      = dtoMapper;
        this.objectMapper   = objectMapper;
    }

    @PostMapping(value = "/analyse-ia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyseIA(
            @RequestPart("declared_data") String declaredDataJson,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer
    ) {
        try {
            CreationDemandeCompleteRequest request =
                    objectMapper.readValue(declaredDataJson, CreationDemandeCompleteRequest.class);

            Map<String, MultipartFile> files = buildFilesMap(
                    cin, fichePaieM1, fichePaieM2, fichePaieM3, attestationTravail, devis, justificatifLoyer);

            if (files.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Au moins un document est requis."));
            }

            AnalyseIAResponse response = analyseIAService.validerAvantCreation(request, files);
            return ResponseEntity.ok(response);

        } catch (CoherenceAnomalyException ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", ex.getMessage());
            body.put("anomalies", ex.getAnomalies());
            if (!ex.getCorrections().isEmpty()) {
                body.put("corrections", ex.getCorrections());
            }
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur analyse IA", "detail", ex.getMessage()));
        }
    }

    @PostMapping(value = "/creation-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DemandeSummaryResponse> creerDemandeComplete(
            @ModelAttribute CreationDemandeCompleteRequest request,
            @RequestParam(value = "recommandations_json", required = false) String recommandationsJson
    ) {
        if (request.getDocuments() == null || request.getDocuments().isEmpty() ||
                request.getDocuments().stream().allMatch(d -> d.getFile() == null || d.getFile().isEmpty())) {
            return ResponseEntity.badRequest().build();
        }

        DemandeFinancement created = demandeService.creerDemandeComplete(request, recommandationsJson);
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toSummary(created));
    }

    @PostMapping("/consentement/confirm")
    public ResponseEntity<DemandeSummaryResponse> validerConsentement(@RequestParam String token) {
        DemandeFinancement updated = demandeService.validerConsentementEtSoumettre(token);
        return ResponseEntity.ok(dtoMapper.toSummary(updated));
    }

    @GetMapping("/par-client")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesParClient(@RequestParam Long clientId) {
        return ResponseEntity.ok(
                demandeService.listerDemandesParClient(clientId)
                        .stream().map(dtoMapper::toSummary).toList());
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<DemandeCompleteResponse> getDemandeDetail(@PathVariable Long id) {
        return ResponseEntity.ok(dtoMapper.toComplete(demandeService.getDemandeParIdPourCommercant(id)));
    }

    @GetMapping("/dossiers/dernier")
    public ResponseEntity<DernierDossierFinancierResponse> getDernierDossierFinancier(@RequestParam String cin) {
        return ResponseEntity.ok(demandeService.getDernierDossierFinancierParCin(cin));
    }

    @GetMapping("/documents/presigned")
    public ResponseEntity<Map<String, String>> getPresignedDocumentUrl(@RequestParam String objectKey) {
        return ResponseEntity.ok(Map.of("url", demandeService.getPresignedDocumentUrl(objectKey)));
    }

    private static Map<String, MultipartFile> buildFilesMap(
            MultipartFile cin,
            MultipartFile fichePaieM1,
            MultipartFile fichePaieM2,
            MultipartFile fichePaieM3,
            MultipartFile attestationTravail,
            MultipartFile devis,
            MultipartFile justificatifLoyer
    ) {
        Map<String, MultipartFile> files = new LinkedHashMap<>();
        if (cin != null && !cin.isEmpty()) files.put("cin", cin);
        if (fichePaieM1 != null && !fichePaieM1.isEmpty()) files.put("fiche_paie_m1", fichePaieM1);
        if (fichePaieM2 != null && !fichePaieM2.isEmpty()) files.put("fiche_paie_m2", fichePaieM2);
        if (fichePaieM3 != null && !fichePaieM3.isEmpty()) files.put("fiche_paie_m3", fichePaieM3);
        if (attestationTravail != null && !attestationTravail.isEmpty()) files.put("attestation_travail", attestationTravail);
        if (devis != null && !devis.isEmpty()) files.put("devis", devis);
        if (justificatifLoyer != null && !justificatifLoyer.isEmpty()) files.put("justificatif_loyer", justificatifLoyer);
        return files;
    }
}
