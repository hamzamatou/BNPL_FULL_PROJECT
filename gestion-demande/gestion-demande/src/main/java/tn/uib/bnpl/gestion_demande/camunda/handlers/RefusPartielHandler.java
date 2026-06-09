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
import tn.uib.bnpl.gestion_demande.camunda.InstructionWorkflowHelper;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_REFUS_PARTIEL,
        processDefinitionKey = "Process_BNPL"
)
public class RefusPartielHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(RefusPartielHandler.class);

    private final InstructionWorkflowHelper instructionHelper;

    public RefusPartielHandler(InstructionWorkflowHelper instructionHelper) {
        this.instructionHelper = instructionHelper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Long demandeId = CamundaHandlerSupport.toLong(externalTask.getVariable("demandeId"));
        if (demandeId == null) {
            log.warn("refus-partiel sans demandeId");
            externalTaskService.complete(externalTask);
            return;
        }
        try {
            instructionHelper.preparerRefusPartiel(demandeId);
            externalTaskService.complete(externalTask);
        } catch (Exception ex) {
            log.error("Worker refus-partiel : {}", ex.getMessage(), ex);
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 3, 15_000);
        }
    }
}
