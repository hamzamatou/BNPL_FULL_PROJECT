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
import tn.uib.bnpl.gestion_demande.camunda.RoutageBanqueWorkflowHelper;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_ROUTAGE,
        processDefinitionKey = "Process_BNPL"
)
public class RoutageBanqueHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(RoutageBanqueHandler.class);

    private final RoutageBanqueWorkflowHelper routageHelper;

    public RoutageBanqueHandler(RoutageBanqueWorkflowHelper routageHelper) {
        this.routageHelper = routageHelper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Long demandeId = toLong(externalTask.getVariable("demandeId"));
        if (demandeId == null) {
            log.warn("routage-banque sans demandeId");
            externalTaskService.complete(externalTask);
            return;
        }
        try {
            routageHelper.routerDemande(demandeId);
            externalTaskService.complete(externalTask);
        } catch (Exception ex) {
            log.error("Worker routage-banque : {}", ex.getMessage(), ex);
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
