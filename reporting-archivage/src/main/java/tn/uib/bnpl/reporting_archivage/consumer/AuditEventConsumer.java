package tn.uib.bnpl.reporting_archivage.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;
import tn.uib.bnpl.reporting_archivage.services.AuditEventService;

@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventService auditEventService;

    public AuditEventConsumer(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @RabbitListener(queues = "${app.rabbit.audit.queue}")
    public void onAuditEvent(AuditEventRequest request) {
        log.debug("Événement audit reçu: {}", request.eventType());
        auditEventService.traiterEvenement(request);
    }
}
