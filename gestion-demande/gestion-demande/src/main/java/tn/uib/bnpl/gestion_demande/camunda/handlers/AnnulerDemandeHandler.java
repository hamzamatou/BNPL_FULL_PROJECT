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
import tn.uib.bnpl.gestion_demande.services.AnnulationWorkflowHelper;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_ANNULER_DEMANDE,
        processDefinitionKey = "Process_BNPL"
)
public class AnnulerDemandeHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AnnulerDemandeHandler.class);

    private final AnnulationWorkflowHelper annulationHelper;

    public AnnulerDemandeHandler(AnnulationWorkflowHelper annulationHelper) {
        this.annulationHelper = annulationHelper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Long demandeId = CamundaHandlerSupport.toLong(externalTask.getVariable("demandeId"));
        if (demandeId == null) {
            log.warn("annuler-demande sans demandeId — instance={}", externalTask.getProcessInstanceId());
            externalTaskService.complete(externalTask);
            return;
        }
        Long acteurUserId = CamundaHandlerSupport.toLong(externalTask.getVariable("acteurUserId"));
        String acteurEmail = CamundaHandlerSupport.toString(externalTask.getVariable("acteurEmail"));
        String acteurRole = CamundaHandlerSupport.toString(externalTask.getVariable("acteurRole"));
        try {
            annulationHelper.appliquerAnnulation(demandeId, acteurUserId, acteurEmail, acteurRole);
            externalTaskService.complete(externalTask);
        } catch (Exception ex) {
            log.error("Worker annuler-demande — demande={} : {}", demandeId, ex.getMessage(), ex);
            externalTaskService.handleFailure(externalTask, ex.getMessage(), ex.toString(), 3, 15_000);
        }
    }
}
