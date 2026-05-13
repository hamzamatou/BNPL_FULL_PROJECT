package tn.uib.bnpl.gestion_demande.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_demande.dto.NotificationEmailRequest;

@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.notification.exchange:notification.exchange}")
    private String exchangeName;

    @Value("${app.rabbit.notification.routing-key:notification.email.request}")
    private String routingKey;

    public NotificationPublisher(
            RabbitTemplate rabbitTemplate,
            Jackson2JsonMessageConverter messageConverter
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMessageConverter(messageConverter);
    }

    public void publishEmail(NotificationEmailRequest payload) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, payload);
        log.info("Notification email publiée: eventId={} correlationId={} to={}",
                payload.eventId(), payload.correlationId(), payload.to());
    }
}

