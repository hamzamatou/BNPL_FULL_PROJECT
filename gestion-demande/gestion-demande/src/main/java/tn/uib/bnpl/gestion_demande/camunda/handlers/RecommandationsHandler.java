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
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_RECOMMANDATIONS,
        processDefinitionKey = "Process_BNPL"
)
public class RecommandationsHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(RecommandationsHandler.class);

    private final CamundaEngineClient engineClient;
    private final WorkflowCoherenceHelper coherenceHelper;
    private final ObjectMapper objectMapper;

    public RecommandationsHandler(CamundaEngineClient engineClient,
                                  WorkflowCoherenceHelper coherenceHelper,
                                  ObjectMapper objectMapper) {
        this.engineClient = engineClient;
        this.coherenceHelper = coherenceHelper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        String instanceId = externalTask.getProcessInstanceId();
        Object coherent = externalTask.getVariable(WorkflowCoherenceHelper.VAR_COHERENT);
        if (!Boolean.TRUE.equals(coherent)) {
            log.warn("Recommandations ignorées — dossier incohérent, instance={}", instanceId);
            engineClient.setProcessVariables(instanceId,
                    Map.of(WorkflowCoherenceHelper.VAR_RECOMMANDATIONS_DONE, true));
            externalTaskService.complete(externalTask);
            return;
        }

        try {
            CreationDemandeCompleteRequest request = objectMapper.readValue(
                    (String) externalTask.getVariable("declaredDataJson"),
                    CreationDemandeCompleteRequest.class);
            List<String> recommandations = coherenceHelper.executerRecommandations(request);
            String json = objectMapper.writeValueAsString(
                    recommandations != null ? recommandations : List.of());

            engineClient.setProcessVariables(instanceId, Map.of(
                    WorkflowCoherenceHelper.VAR_RECOMMANDATIONS_DONE, true,
                    "recommandationsJson", json
            ));
            log.info("Recommandations générées — instance={} count={}", instanceId, recommandations.size());
            externalTaskService.complete(externalTask);
        } catch (Exception ex) {
            log.error("Worker recommandations : {}", ex.getMessage(), ex);
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 3, 15_000);
        }
    }
}
