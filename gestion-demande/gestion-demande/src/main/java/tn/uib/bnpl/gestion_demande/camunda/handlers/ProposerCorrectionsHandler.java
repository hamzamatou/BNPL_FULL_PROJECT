package tn.uib.bnpl.gestion_demande.camunda.handlers;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_CORRECTIONS,
        processDefinitionKey = "Process_BNPL"
)
public class ProposerCorrectionsHandler implements ExternalTaskHandler {

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        externalTaskService.complete(externalTask);
    }
}
