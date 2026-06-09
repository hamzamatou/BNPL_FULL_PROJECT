package tn.uib.bnpl.gestion_demande.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;
import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;
import tn.uib.bnpl.gestion_demande.services.AnalyseIAService;
import tn.uib.bnpl.gestion_demande.services.AnalyseOutcomeCache;
import tn.uib.bnpl.gestion_demande.services.DemandeService;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Flux en deux appels :
 *
 *  POST /api/demandes/analyse           → cohérence puis reco si OK (un seul appel front)
 *  POST /api/demandes/coherence         → cohérence ; si anomalies vides, reco micro puis réponse unique
 *  POST /api/demandes/recommandations   → étape 2 seule (rétrocompat / debug)
 *  POST /api/demandes/analyse-ia        → alias de /analyse
 *
 *  POST /api/demandes/creation-complete → persiste (reco déjà calculées côté front)
 *       → 201 DemandeSummaryResponse
 */
@RestController
@RequestMapping("/api/demandes")
public class DemandeController {

    private final DemandeService   demandeService;
    private final AnalyseIAService analyseIAService;
    private final AnalyseOutcomeCache analyseOutcomeCache;
    private final Optional<CamundaWorkflowService> camundaWorkflowService;
    private final DemandeDtoMapper dtoMapper;
    private final ObjectMapper     objectMapper;

    public DemandeController(DemandeService   demandeService,
                             AnalyseIAService analyseIAService,
                             AnalyseOutcomeCache analyseOutcomeCache,
                             Optional<CamundaWorkflowService> camundaWorkflowService,
                             DemandeDtoMapper dtoMapper,
                             ObjectMapper     objectMapper) {
        this.demandeService   = demandeService;
        this.analyseIAService = analyseIAService;
        this.analyseOutcomeCache = analyseOutcomeCache;
        this.camundaWorkflowService = camundaWorkflowService;
        this.dtoMapper      = dtoMapper;
        this.objectMapper   = objectMapper;
    }

    @PostMapping(value = "/coherence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> verifierCoherence(
            @RequestPart("declared_data") String declaredDataJson,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer,
            @RequestParam(value = "process_instance_id", required = false) String processInstanceId
    ) {
        return handleCoherence(declaredDataJson, cin, fichePaieM1, fichePaieM2, fichePaieM3,
                attestationTravail, devis, justificatifLoyer, processInstanceId);
    }

    @PostMapping("/recommandations")
    public ResponseEntity<?> obtenirRecommandations(
            @RequestParam(value = "process_instance_id", required = false) String processInstanceId,
            @RequestParam(value = "analysis_session_id", required = false) String analysisSessionId
    ) {
        try {
            if (camundaWorkflowService.isPresent()) {
                if (processInstanceId == null || processInstanceId.isBlank()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "process_instance_id requis"));
                }
                RecommandationsResponse response =
                        camundaWorkflowService.get().executerRecommandations(processInstanceId);
                return ResponseEntity.ok(response);
            }
            AnalyseOutcomeCache.CachedAnalyseSession cached = analyseOutcomeCache.getAndRemove(analysisSessionId);
            if (cached == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Session d'analyse invalide ou expirée"));
            }
            List<String> recommandations = analyseIAService.executerRecommandations(cached.request());
            return ResponseEntity.ok(new RecommandationsResponse(recommandations, null));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur recommandations", "detail", ex.getMessage()));
        }
    }

    /**
     * Point d'entrée commerçant : vérifie la cohérence puis enchaîne les recommandations
     * si aucune anomalie (workers Camunda ou IA directe).
     */
    @PostMapping(value = "/analyse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyser(
            @RequestPart("declared_data") String declaredDataJson,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer,
            @RequestParam(value = "process_instance_id", required = false) String processInstanceId
    ) {
        return handleAnalyse(declaredDataJson, cin, fichePaieM1, fichePaieM2, fichePaieM3,
                attestationTravail, devis, justificatifLoyer, processInstanceId);
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
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer,
            @RequestParam(value = "process_instance_id", required = false) String processInstanceId
    ) {
        return handleAnalyse(declaredDataJson, cin, fichePaieM1, fichePaieM2, fichePaieM3,
                attestationTravail, devis, justificatifLoyer, processInstanceId);
    }

    private ResponseEntity<?> handleAnalyse(
            String declaredDataJson,
            MultipartFile cin,
            MultipartFile fichePaieM1,
            MultipartFile fichePaieM2,
            MultipartFile fichePaieM3,
            MultipartFile attestationTravail,
            MultipartFile devis,
            MultipartFile justificatifLoyer,
            String processInstanceId
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

            String instanceId = processInstanceId;
            AnalyseIAResponse response;
            if (camundaWorkflowService.isPresent()) {
                if (instanceId == null || instanceId.isBlank()) {
                    instanceId = camundaWorkflowService.get().startWorkflow(request, files).processInstanceId();
                }
                response = camundaWorkflowService.get().executerAnalyseComplete(instanceId, request, files);
            } else {
                response = analyseIAService.analyserAvantCreation(request, files);
            }
            return ResponseEntity.ok(response);

        } catch (CoherenceAnomalyException ex) {
            return coherenceErrorResponse(ex);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur analyse", "detail", ex.getMessage()));
        }
    }

    private ResponseEntity<?> handleCoherence(
            String declaredDataJson,
            MultipartFile cin,
            MultipartFile fichePaieM1,
            MultipartFile fichePaieM2,
            MultipartFile fichePaieM3,
            MultipartFile attestationTravail,
            MultipartFile devis,
            MultipartFile justificatifLoyer,
            String processInstanceId
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

            String instanceId = processInstanceId;
            if (camundaWorkflowService.isPresent()) {
                if (instanceId == null || instanceId.isBlank()) {
                    instanceId = camundaWorkflowService.get().startWorkflow(request, files).processInstanceId();
                }
                CoherenceResponse coherenceCamunda =
                        camundaWorkflowService.get().executerCoherence(instanceId, request, files);
                // Anomalies vides (sinon 422) → recommandations via micro IA, pas la variable Camunda
                List<String> recommandations = analyseIAService.executerRecommandations(request);
                return ResponseEntity.ok(new CoherenceResponse(
                        true,
                        coherenceCamunda.processInstanceId(),
                        null,
                        coherenceCamunda.corrections(),
                        coherenceCamunda.alertes(),
                        recommandations
                ));
            }

            AnalyseIAService.CoherenceAvecRecommandationsResult result =
                    analyseIAService.executerCoherencePuisRecommandations(request, files);
            String sessionId = analyseOutcomeCache.putCoherenceOk(
                    new AnalyseOutcomeCache.CachedAnalyseSession(
                            request,
                            result.corrections(),
                            result.alertes()
                    ));
            return ResponseEntity.ok(new CoherenceResponse(
                    true,
                    null,
                    sessionId,
                    result.corrections(),
                    result.alertes(),
                    result.recommandations()
            ));

        } catch (CoherenceAnomalyException ex) {
            return coherenceErrorResponse(ex);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Erreur analyse cohérence", "detail", ex.getMessage()));
        }
    }

    private static ResponseEntity<Map<String, Object>> coherenceErrorResponse(CoherenceAnomalyException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("anomalies", ex.getAnomalyItems());
        if (!ex.getCorrections().isEmpty()) {
            body.put("corrections", ex.getCorrections());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Même enveloppe multipart que {@code /analyse-ia} : {@code declared_data} (JSON sans binaires)
     * + parties nommées {@code cin}, {@code fiche_paie_m1}, etc.
     * L'ancien format {@code documents[i].file} n'était pas lié correctement par {@code @ModelAttribute}.
     */
    @PostMapping(value = "/creation-complete", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> creerDemandeComplete(
            @RequestPart("declared_data") String declaredDataJson,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer,
            @RequestParam(value = "recommandations_json", required = false) String recommandationsJson,
            @RequestParam(value = "process_instance_id", required = false) String processInstanceId
    ) {
        try {
            CreationDemandeCompleteRequest request =
                    objectMapper.readValue(declaredDataJson, CreationDemandeCompleteRequest.class);

            Map<String, MultipartFile> files = buildFilesMap(
                    cin, fichePaieM1, fichePaieM2, fichePaieM3, attestationTravail, devis, justificatifLoyer);

            if (files.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Au moins un document est requis pour la création."));
            }

            attachDocumentsFromParts(request, files);

            DemandeFinancement created = demandeService.creerDemandeComplete(
                    request, recommandationsJson, processInstanceId);
            return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toSummary(created));
        } catch (JsonProcessingException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "declared_data JSON invalide.", "detail", ex.getMessage()));
        }
    }

    @PostMapping("/consentement/confirm")
    public ResponseEntity<DemandeSummaryResponse> validerConsentement(@RequestParam String token) {
        DemandeFinancement updated = demandeService.validerConsentementEtSoumettre(token);
        return ResponseEntity.ok(dtoMapper.toSummary(updated));
    }

    @GetMapping("/admin/en-cours")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesEnCoursAdmin() {
        return ResponseEntity.ok(demandeService.listerDemandesEnCoursPourAdmin());
    }

    @GetMapping("/par-client")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesParClient(@RequestParam Long clientId) {
        return ResponseEntity.ok(
                demandeService.listerDemandesParClient(clientId)
                        .stream().map(dtoMapper::toSummary).toList());
    }

    @GetMapping("/par-commercant/{commercantId}")
    @PreAuthorize("hasAuthority('COMMERCANT')")
    public ResponseEntity<List<DemandeSummaryResponse>> listerDemandesParCommercantId(
            @PathVariable Long commercantId) {
        return ResponseEntity.ok(demandeService.listerDemandesParCommercant(commercantId));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<DemandeCompleteResponse> getDemandeDetail(@PathVariable Long id) {
        return ResponseEntity.ok(dtoMapper.toComplete(demandeService.getDemandeParIdPourCommercant(id)));
    }

    @PostMapping("/{id}/annuler")
    @PreAuthorize("hasAuthority('COMMERCANT')")
    public ResponseEntity<DemandeSummaryResponse> annulerDemande(@PathVariable Long id) {
        DemandeFinancement updated = demandeService.annulerDemande(id);
        return ResponseEntity.ok(dtoMapper.toSummary(updated));
    }

    @PostMapping("/{id}/renvoyer-consentement")
    @PreAuthorize("hasAuthority('COMMERCANT')")
    public ResponseEntity<DemandeSummaryResponse> renvoyerMailConsentement(@PathVariable Long id) {
        DemandeFinancement updated = demandeService.renvoyerMailConsentement(id);
        return ResponseEntity.ok(dtoMapper.toSummary(updated));
    }

    @GetMapping("/dossiers/dernier")
    public ResponseEntity<DernierDossierFinancierResponse> getDernierDossierFinancier(@RequestParam String cin) {
        return ResponseEntity.ok(demandeService.getDernierDossierFinancierParCin(cin));
    }

    @GetMapping("/documents/presigned")
    public ResponseEntity<Map<String, String>> getPresignedDocumentUrl(@RequestParam String objectKey) {
        return ResponseEntity.ok(Map.of("url", demandeService.getPresignedDocumentUrl(objectKey)));
    }

    public static Map<String, MultipartFile> buildFilesMapPublic(
            MultipartFile cin,
            MultipartFile fichePaieM1,
            MultipartFile fichePaieM2,
            MultipartFile fichePaieM3,
            MultipartFile attestationTravail,
            MultipartFile devis,
            MultipartFile justificatifLoyer
    ) {
        return buildFilesMap(cin, fichePaieM1, fichePaieM2, fichePaieM3, attestationTravail, devis, justificatifLoyer);
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

    private static void attachDocumentsFromParts(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files) {
        List<CreationDemandeCompleteRequest.DocumentMultipart> list = new ArrayList<>();
        for (Map.Entry<String, MultipartFile> e : files.entrySet()) {
            MultipartFile f = e.getValue();
            if (f == null || f.isEmpty()) {
                continue;
            }
            CreationDemandeCompleteRequest.DocumentMultipart d =
                    new CreationDemandeCompleteRequest.DocumentMultipart();
            d.setTypeDocument(e.getKey());
            d.setFile(f);
            list.add(d);
        }
        request.setDocuments(list);
    }
}
