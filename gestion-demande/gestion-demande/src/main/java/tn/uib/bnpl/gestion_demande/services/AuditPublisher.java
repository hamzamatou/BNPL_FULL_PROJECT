package tn.uib.bnpl.gestion_demande.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_demande.dto.audit.AuditEventRequest;

/**
 * Publication des événements d'audit vers reporting-archivage via RabbitMQ.
 */
@Service
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.audit.exchange:audit.exchange}")
    private String exchangeName;

    @Value("${app.rabbit.audit.routing-key:audit.event}")
    private String routingKey;

    @Value("${app.audit.rabbit.enabled:true}")
    private boolean enabled;

    public AuditPublisher(
            RabbitTemplate rabbitTemplate,
            Jackson2JsonMessageConverter messageConverter) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMessageConverter(messageConverter);
    }

    public void publier(AuditEventRequest request) {
        if (!enabled) {
            log.warn("Audit RabbitMQ désactivé — événement ignoré: {}", request.eventType());
            return;
        }
        try {
            rabbitTemplate.convertAndSend(exchangeName, routingKey, request);
            log.debug("Événement audit publié: type={} demandeId={} correlationId={}",
                    request.eventType(),
                    request.payload() != null ? request.payload().demandeId() : null,
                    request.correlationId());
        } catch (AmqpException ex) {
            throw new IllegalStateException(
                    "Impossible de publier l'événement audit sur RabbitMQ: " + request.eventType(),
                    ex);
        }
    }
}
