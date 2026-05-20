package tn.uib.bnpl.reporting_archivage.services;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;

@Component
public class AuditPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public AuditPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.rabbit.audit.exchange}") String exchange,
            @Value("${app.rabbit.audit.routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publier(AuditEventRequest request) {
        rabbitTemplate.convertAndSend(exchange, routingKey, request);
    }
}
