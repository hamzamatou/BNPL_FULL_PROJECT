package tn.uib.bnpl.gestion_demande.camunda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.dto.AnalyseIAResponse;
import tn.uib.bnpl.gestion_demande.dto.CoherenceResponse;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.PrescoringResultDto;
import tn.uib.bnpl.gestion_demande.dto.RecommandationsResponse;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
public class CamundaWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(CamundaWorkflowService.class);

    /** Commerçant : analyse IA (cohérence puis recommandations si OK). */
    public static final String TASK_ANALYSER_DEMANDE = "Task_AnalyserDemande";
    /** Commerçant : enregistrement BDD + email consentement (POST /creation-complete). */
    public static final String TASK_SOUMETTRE_DEMANDE = "Task_SoumettreDemande";
    /** Client : consentement par lien email (POST /consentement/confirm). */
    public static final String TASK_VALIDER_CONSENTEMENT = "Task_ValiderConsentement";

    /** Service externe : vérification cohérence (id BPMN = activityId secours API). */
    public static final String TASK_VERIFIER_COHERENCE = "Task_VerifierCoherence";
    /** Service externe : génération recommandations. */
    public static final String TASK_GENERER_RECO = "Task_GenererRecommandations";
    /** Service externe : proposer corrections (branche incohérent). */
    public static final String TASK_PROPOSER_CORRECTIONS = "Activity_0juifhr";

    public static final String TOPIC_VERIFICATION = "verification-coherence";
    public static final String TOPIC_RECOMMANDATIONS = "recommandations";
    public static final String TOPIC_CORRECTIONS = "proposer-corrections";
    public static final String TOPIC_PRESCORING = "prescoring";
    public static final String TOPIC_ROUTAGE = "routage-banque";
    public static final String TOPIC_EXPIRATION_PRISE_EN_CHARGE = "expiration-prise-en-charge";
    public static final String TOPIC_DEMANDER_COMPLEMENT = "demander-complement";
    public static final String TOPIC_CLOTURER_ARCHIVER_DEMANDE = "cloturer-archiver-demande";
    public static final String TOPIC_REFUS_PARTIEL = "refus-partiel";
    public static final String TOPIC_ANNULER_DEMANDE = "annuler-demande";

    public static final String MESSAGE_ANNULATION_COMMERCANT = "Message_AnnulationCommercant";
    public static final String TASK_ANNULER_DEMANDE = "Task_AnnulerDemande";
    public static final String VAR_ANNULATION_AUTORISEE = "annulationAutorisee";

    private static final long ANNULATION_WAIT_MS = 30_000;

    public static final String TASK_PRISE_EN_CHARGE = "Task_PriseEnCharge";
    public static final String TASK_PRENDRE_DECISION = "Task_PrendreDecision";
    public static final String TASK_DEMANDER_COMPLEMENT = "Task_DemanderComplement";
    public static final String TASK_CLOTURER_ARCHIVER_DEMANDE = "Task_CloreArchiverDemande";
    public static final String END_EVENT_CLOTUREE = "EndEvent_Cloturee";
    public static final String TASK_PREPARER_REFUS_PARTIEL = "Task_PreparerRefusPartiel";
    public static final String MESSAGE_DEMARRER_ANALYSE = "demarrerAnalyse";

    /** Seuil rejet auto prescoring — aligné sur prescoring_service.py (PD strictement &gt; 60 %). */
    public static final double SEUIL_REJET_AUTO_PD_PCT = 60.0;

    private final CamundaEngineClient engineClient;
    private final WorkflowDocumentStagingService stagingService;
    private final ObjectMapper objectMapper;

    public CamundaWorkflowService(CamundaEngineClient engineClient,
                                  WorkflowDocumentStagingService stagingService,
                                  ObjectMapper objectMapper) {
        this.engineClient = engineClient;
        this.stagingService = stagingService;
        this.objectMapper = objectMapper;
    }

    public WorkflowStartResult startWorkflow(CreationDemandeCompleteRequest request,
                                             Map<String, MultipartFile> files) {
        String businessKey = "BNPL-" + UUID.randomUUID();
        String declaredJson;
        try {
            declaredJson = objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            throw new IllegalArgumentException("declared_data invalide", ex);
        }
        String documentKeysJson = stagingService.stageDocuments(businessKey, files);

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("businessKey", businessKey);
        vars.put("declaredDataJson", declaredJson);
        vars.put("documentKeysJson", documentKeysJson);
        vars.put(VAR_ANNULATION_AUTORISEE, true);

        String processInstanceId = engineClient.startProcess(businessKey, vars);
        log.info("Processus Camunda démarré — instance={} businessKey={}", processInstanceId, businessKey);
        return new WorkflowStartResult(processInstanceId, businessKey);
    }

    private static final long COHERENCE_WAIT_MS = 120_000;
    private static final long RECOMMANDATIONS_WAIT_MS = 60_000;

    /**
     * Étape 1 : fin « Analyser » puis worker {@code verification-coherence}.
     */
    public CoherenceResponse executerCoherence(String processInstanceId,
                                               CreationDemandeCompleteRequest request,
                                               Map<String, MultipartFile> files) {
        refreshStaging(processInstanceId, request, files);

        engineClient.completeUserTaskForProcess(processInstanceId, TASK_ANALYSER_DEMANDE, Map.of());
        engineClient.waitForExternalTaskProcessed(
                processInstanceId,
                TOPIC_VERIFICATION,
                TASK_VERIFIER_COHERENCE,
                COHERENCE_WAIT_MS,
                WorkflowCoherenceHelper.VAR_COHERENCE_DONE);

        Object coherent = engineClient.getProcessVariable(processInstanceId, WorkflowCoherenceHelper.VAR_COHERENT);
        if (!Boolean.TRUE.equals(coherent)) {
            throw buildCoherenceExceptionFromProcess(processInstanceId);
        }

        Map<String, Object> corrections = lireCorrectionsProcess(processInstanceId);
        return new CoherenceResponse(true, processInstanceId, null, corrections, List.of(), List.of());
    }

    /** Corrections OCR renvoyées par le worker cohérence (variable processus). */
    public Map<String, Object> lireCorrectionsProcess(String processInstanceId) {
        return parseCorrectionsJson(stringVar(engineClient.getProcessVariableQuiet(
                processInstanceId, "correctionsJson")));
    }

    /**
     * Analyse complète : cohérence (worker) puis recommandations si {@code coherent == true}.
     * Décision entièrement côté serveur — le front n'appelle qu'un seul endpoint.
     */
    public AnalyseIAResponse executerAnalyseComplete(String processInstanceId,
                                                     CreationDemandeCompleteRequest request,
                                                     Map<String, MultipartFile> files) {
        CoherenceResponse coherence = executerCoherence(processInstanceId, request, files);
        RecommandationsResponse reco = executerRecommandations(coherence.processInstanceId());
        return new AnalyseIAResponse(
                reco.recommandations(),
                Map.of(),
                List.of(),
                coherence.processInstanceId());
    }

    /**
     * Étape 2 : worker {@code recommandations} (reco déjà calculées à l'étape cohérence).
     */
    public RecommandationsResponse executerRecommandations(String processInstanceId) {
        Object coherent = engineClient.getProcessVariable(processInstanceId, WorkflowCoherenceHelper.VAR_COHERENT);
        if (!Boolean.TRUE.equals(coherent)) {
            throw new IllegalStateException("Recommandations indisponibles : dossier non cohérent");
        }

        // Reco déjà dans le processus après cohérence ; fermer l'étape Camunda (worker souvent plus rapide que le poll).
        engineClient.completeExternalTaskForProcess(
                processInstanceId,
                TOPIC_RECOMMANDATIONS,
                TASK_GENERER_RECO,
                Map.of(),
                RECOMMANDATIONS_WAIT_MS);

        if (!engineClient.hasUserTask(processInstanceId, TASK_SOUMETTRE_DEMANDE)
                && !isTruthy(engineClient.getProcessVariableQuiet(
                        processInstanceId, WorkflowCoherenceHelper.VAR_RECOMMANDATIONS_DONE))) {
            engineClient.waitForExternalTaskProcessed(
                    processInstanceId,
                    TOPIC_RECOMMANDATIONS,
                    TASK_GENERER_RECO,
                    RECOMMANDATIONS_WAIT_MS,
                    WorkflowCoherenceHelper.VAR_RECOMMANDATIONS_DONE);
        }

        String json = stringVar(engineClient.getProcessVariableQuiet(processInstanceId, "recommandationsJson"));
        List<String> reco = parseRecommandationsJson(json);
        return new RecommandationsResponse(reco, processInstanceId);
    }

    private CoherenceAnomalyException buildCoherenceExceptionFromProcess(String processInstanceId) {
        String message = stringVar(engineClient.getProcessVariableQuiet(
                processInstanceId, WorkflowCoherenceHelper.VAR_MESSAGE_COHERENCE));
        if (message.isBlank()) {
            message = "Incohérences détectées dans le dossier";
        }
        List<String> anomalies = parseAnomaliesJson(stringVar(engineClient.getProcessVariableQuiet(
                processInstanceId, WorkflowCoherenceHelper.VAR_ANOMALIES_JSON)));
        Map<String, Object> corrections = parseCorrectionsJson(stringVar(engineClient.getProcessVariableQuiet(
                processInstanceId, "correctionsJson")));
        return CoherenceAnomalyException.fromMessages(message, anomalies, corrections);
    }

    private List<String> parseRecommandationsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseAnomaliesJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of("Incohérences détectées");
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of(json);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCorrectionsJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String stringVar(Object v) {
        return v == null ? "" : v.toString();
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(value.toString().trim());
    }

    /**
     * Commerçant : soumission officielle (creation-complete + email consentement).
     */
    public void soumettreDemandeCommercant(String processInstanceId, Long demandeId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        if (demandeId != null) {
            engineClient.setProcessVariables(processInstanceId, Map.of("demandeId", demandeId));
        }
        engineClient.completeUserTaskForProcess(processInstanceId, TASK_SOUMETTRE_DEMANDE, Map.of());
        log.info("Demande soumise (creation-complete) — instance={} demandeId={}", processInstanceId, demandeId);
    }

    public void validerConsentementClient(String processInstanceId) {
        engineClient.completeUserTaskForProcess(processInstanceId, TASK_VALIDER_CONSENTEMENT, Map.of());
    }

    public void completePriseEnCharge(String processInstanceId, LocalDateTime dateExpiration, Long banqueUserId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("dateExpiration", dateExpiration.toString());
        vars.put(VAR_ANNULATION_AUTORISEE, false);
        if (banqueUserId != null) {
            vars.put("banqueUserId", banqueUserId);
        }
        engineClient.completeUserTaskForProcess(processInstanceId, TASK_PRISE_EN_CHARGE, vars);
        log.info("Camunda — prise en charge complétée — instance={}", processInstanceId);
    }

    /**
     * Commerçant : message {@link #MESSAGE_ANNULATION_COMMERCANT} → subprocess {@link #TASK_ANNULER_DEMANDE}.
     */
    public void declencherAnnulationCommercant(String processInstanceId,
                                               Long demandeId,
                                               Long acteurUserId,
                                               String acteurEmail,
                                               String acteurRole) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("processInstanceId requis pour l'annulation Camunda");
        }
        Object autorise = engineClient.getProcessVariableQuiet(processInstanceId, VAR_ANNULATION_AUTORISEE);
        if (autorise != null && !isTruthy(autorise)) {
            throw new IllegalStateException("Annulation interdite : prise en charge déjà effectuée");
        }

        Map<String, Object> vars = new LinkedHashMap<>();
        if (demandeId != null) {
            vars.put("demandeId", demandeId);
        }
        if (acteurUserId != null) {
            vars.put("acteurUserId", acteurUserId);
        }
        if (acteurEmail != null && !acteurEmail.isBlank()) {
            vars.put("acteurEmail", acteurEmail);
        }
        if (acteurRole != null && !acteurRole.isBlank()) {
            vars.put("acteurRole", acteurRole);
        }

        engineClient.correlateMessage(processInstanceId, MESSAGE_ANNULATION_COMMERCANT, vars);
        engineClient.waitForExternalTaskProcessed(
                processInstanceId,
                TOPIC_ANNULER_DEMANDE,
                TASK_ANNULER_DEMANDE,
                ANNULATION_WAIT_MS);
        log.info("Annulation Camunda terminée — instance={} demandeId={}", processInstanceId, demandeId);
    }

    /** @deprecated Plus de sous-processus message : se-saisir enchaîne directement sur Instruire. */
    @Deprecated
    public void completeDemarrerAnalyse(String processInstanceId) {
        log.debug("completeDemarrerAnalyse ignoré (flux direct PEC → Instruire) — instance={}", processInstanceId);
    }

    public void completeInstruction(String processInstanceId,
                                    String decision,
                                    boolean refusPartiel,
                                    String commentaire,
                                    String motifRefus,
                                    Long banqueUserId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("decision", decision);
        if (refusPartiel) {
            vars.put("refusPartiel", true);
        }
        if (commentaire != null && !commentaire.isBlank()) {
            vars.put("commentaireComplement", commentaire);
        }
        if (motifRefus != null && !motifRefus.isBlank()) {
            vars.put("motifRefus", motifRefus);
        }
        if (banqueUserId != null) {
            vars.put("banqueUserId", banqueUserId);
        }
        if ("ACCEPTER".equals(decision)) {
            vars.put("statutCloture", "ACCEPTEE");
        } else if ("REFUSER".equals(decision) && !refusPartiel) {
            vars.put("statutCloture", "REFUSEE");
        }
        engineClient.completeUserTaskForProcess(processInstanceId, TASK_PRENDRE_DECISION, vars);
        log.info("Camunda — instruction complétée decision={} refusPartiel={} — instance={}",
                decision, refusPartiel, processInstanceId);
    }

    public void advancePrescoringAndRoutage(String processInstanceId, boolean scoreOk) {
        Map<String, Object> prescoringVars = Map.of("scoreOk", scoreOk);
        engineClient.completeExternalTaskForProcess(
                processInstanceId, TOPIC_PRESCORING, "Task_Prescoring", prescoringVars);
        if (scoreOk) {
            engineClient.completeExternalTaskForProcess(
                    processInstanceId, TOPIC_ROUTAGE, "Task_RouterDemande", Map.of());
        }
    }

    public boolean isScoreOk(PrescoringResultDto dto) {
        return !isRejetAutoPrescoring(dto);
    }

    /** Rejet auto : PD &gt; 60 % (pas la zone orange à 60 % inclus). */
    public boolean isRejetAutoPrescoring(PrescoringResultDto dto) {
        if (dto == null || dto.pdPct() == null) {
            return false;
        }
        return dto.pdPct() > SEUIL_REJET_AUTO_PD_PCT;
    }

    public boolean isRejetAutoPrescoring(PrescoringScore score) {
        return score != null && score.getProbabiliteDefaut() > SEUIL_REJET_AUTO_PD_PCT;
    }

    private void refreshStaging(String processInstanceId,
                                CreationDemandeCompleteRequest request,
                                Map<String, MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        Object bk = engineClient.getProcessVariable(processInstanceId, "businessKey");
        String businessKey = bk != null ? bk.toString() : processInstanceId;
        String documentKeysJson = stagingService.stageDocuments(businessKey, files);
        try {
            String declaredJson = objectMapper.writeValueAsString(request);
            engineClient.setProcessVariables(processInstanceId, Map.of(
                    "declaredDataJson", declaredJson,
                    "documentKeysJson", documentKeysJson
            ));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Mise à jour variables workflow", ex);
        }
    }

    public record WorkflowStartResult(String processInstanceId, String businessKey) {}
}
