package tn.uib.bnpl.gestion_demande.camunda.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.camunda.CamundaEngineClient;
import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;
import tn.uib.bnpl.gestion_demande.camunda.WorkflowCoherenceHelper;
import tn.uib.bnpl.gestion_demande.camunda.WorkflowDocumentStagingService;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_VERIFICATION,
        processDefinitionKey = "Process_BNPL"
)
public class VerificationCoherenceHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(VerificationCoherenceHandler.class);

    private final WorkflowCoherenceHelper coherenceHelper;
    private final WorkflowDocumentStagingService stagingService;
    private final ObjectMapper objectMapper;
    private final CamundaEngineClient engineClient;

    public VerificationCoherenceHandler(WorkflowCoherenceHelper coherenceHelper,
                                          WorkflowDocumentStagingService stagingService,
                                          ObjectMapper objectMapper,
                                          CamundaEngineClient engineClient) {
        this.coherenceHelper = coherenceHelper;
        this.stagingService = stagingService;
        this.objectMapper = objectMapper;
        this.engineClient = engineClient;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        String instanceId = externalTask.getProcessInstanceId();
        try {
            CreationDemandeCompleteRequest request = objectMapper.readValue(
                    (String) externalTask.getVariable("declaredDataJson"),
                    CreationDemandeCompleteRequest.class);
            String documentKeysJson = (String) externalTask.getVariable("documentKeysJson");
            Map<String, org.springframework.web.multipart.MultipartFile> files =
                    stagingService.loadStagedFiles(documentKeysJson);

            WorkflowCoherenceHelper.CoherenceExecutionResult result =
                    coherenceHelper.executerValidation(request, files);

            engineClient.setProcessVariables(instanceId, result.processVariables());
            externalTaskService.complete(externalTask);

            if (!result.success()) {
                engineClient.completeExternalTaskForProcess(
                        instanceId,
                        CamundaWorkflowService.TOPIC_CORRECTIONS,
                        CamundaWorkflowService.TASK_PROPOSER_CORRECTIONS,
                        Map.of(),
                        20_000);
                log.info("Cohérence KO — corrections proposées, instance={}", instanceId);
            } else {
                log.info("Cohérence OK — instance={}", instanceId);
            }
        } catch (CoherenceAnomalyException ex) {
            log.warn("Cohérence handler anomaly : {}", ex.getMessage());
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 2, 10_000);
        } catch (Exception ex) {
            log.error("Worker verification-coherence : {}", ex.getMessage(), ex);
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 3, 15_000);
        }
    }
}
