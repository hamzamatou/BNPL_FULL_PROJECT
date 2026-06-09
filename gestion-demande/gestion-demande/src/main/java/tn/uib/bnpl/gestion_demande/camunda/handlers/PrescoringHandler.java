package tn.uib.bnpl.gestion_demande.camunda.handlers;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;
import tn.uib.bnpl.gestion_demande.camunda.PrescoringWorkflowHelper;
import tn.uib.bnpl.gestion_demande.camunda.RejetAutoPrescoringWorkflowHelper;
import tn.uib.bnpl.gestion_demande.dto.PrescoringResultDto;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_PRESCORING,
        processDefinitionKey = "Process_BNPL"
)
public class PrescoringHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(PrescoringHandler.class);

    private final PrescoringWorkflowHelper prescoringHelper;
    private final RejetAutoPrescoringWorkflowHelper rejetAutoHelper;
    private final CamundaWorkflowService workflowService;

    public PrescoringHandler(PrescoringWorkflowHelper prescoringHelper,
                             RejetAutoPrescoringWorkflowHelper rejetAutoHelper,
                             CamundaWorkflowService workflowService) {
        this.prescoringHelper = prescoringHelper;
        this.rejetAutoHelper = rejetAutoHelper;
        this.workflowService = workflowService;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Long demandeId = toLong(externalTask.getVariable("demandeId"));
        if (demandeId == null) {
            log.warn("prescoring sans demandeId — instance {}", externalTask.getProcessInstanceId());
            externalTaskService.complete(externalTask, Map.of("scoreOk", false));
            return;
        }
        try {
            PrescoringResultDto dto = prescoringHelper.executerPrescoring(demandeId);
            boolean scoreOk = workflowService.isScoreOk(dto);
            if (workflowService.isRejetAutoPrescoring(dto)) {
                rejetAutoHelper.traiterRejetAuto(demandeId);
            }
            externalTaskService.complete(externalTask, Map.of("scoreOk", scoreOk));
        } catch (Exception ex) {
            log.error("Worker prescoring : {}", ex.getMessage(), ex);
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 3, 15_000);
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
