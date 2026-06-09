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
import tn.uib.bnpl.gestion_demande.camunda.ExpirationPriseEnChargeWorkflowHelper;

@Component
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")
@ExternalTaskSubscription(
        topicName = CamundaWorkflowService.TOPIC_EXPIRATION_PRISE_EN_CHARGE,
        processDefinitionKey = "Process_BNPL"
)
public class ExpirationPriseEnChargeHandler implements ExternalTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(ExpirationPriseEnChargeHandler.class);

    private final ExpirationPriseEnChargeWorkflowHelper expirationHelper;

    public ExpirationPriseEnChargeHandler(ExpirationPriseEnChargeWorkflowHelper expirationHelper) {
        this.expirationHelper = expirationHelper;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        Long demandeId = toLong(externalTask.getVariable("demandeId"));
        if (demandeId == null) {
            log.warn("expiration-prise-en-charge sans demandeId");
            externalTaskService.complete(externalTask);
            return;
        }
        try {
            expirationHelper.expirerFenetre48h(demandeId);
            externalTaskService.complete(externalTask);
        } catch (Exception ex) {
            log.error("Worker expiration-prise-en-charge : {}", ex.getMessage(), ex);
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
