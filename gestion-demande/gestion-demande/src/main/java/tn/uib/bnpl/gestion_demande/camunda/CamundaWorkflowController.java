package tn.uib.bnpl.gestion_demande.camunda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.controllers.DemandeController;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demandes/workflow")
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
public class CamundaWorkflowController {

    private final CamundaWorkflowService workflowService;
    private final CamundaEngineClient engineClient;
    private final ObjectMapper objectMapper;

    public CamundaWorkflowController(CamundaWorkflowService workflowService,
                                     CamundaEngineClient engineClient,
                                     ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.engineClient = engineClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/start", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> startWorkflow(
            @RequestPart("declared_data") String declaredDataJson,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer
    ) throws Exception {
        CreationDemandeCompleteRequest request =
                objectMapper.readValue(declaredDataJson, CreationDemandeCompleteRequest.class);
        Map<String, MultipartFile> files = DemandeController.buildFilesMapPublic(
                cin, fichePaieM1, fichePaieM2, fichePaieM3, attestationTravail, devis, justificatifLoyer);
        if (files.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Au moins un document est requis."));
        }
        CamundaWorkflowService.WorkflowStartResult result = workflowService.startWorkflow(request, files);
        return ResponseEntity.ok(Map.of(
                "processInstanceId", result.processInstanceId(),
                "businessKey", result.businessKey()
        ));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> listActiveTasks(
            @RequestParam String processInstanceId) {
        return ResponseEntity.ok(engineClient.getTasks(processInstanceId, null));
    }

    @PostMapping("/tasks/{taskDefinitionKey}/complete")
    public ResponseEntity<Void> completeUserTask(
            @PathVariable String taskDefinitionKey,
            @RequestParam String processInstanceId,
            @RequestBody(required = false) Map<String, Object> variables) {
        engineClient.completeUserTaskForProcess(processInstanceId, taskDefinitionKey,
                variables != null ? variables : Map.of());
        return ResponseEntity.noContent().build();
    }
}
