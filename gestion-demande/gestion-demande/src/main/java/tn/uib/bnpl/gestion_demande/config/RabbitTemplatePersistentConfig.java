package tn.uib.bnpl.gestion_demande.config;

import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTemplatePersistentConfig {

    /**
     * Garantit que les notifications restent persistees sur le broker (survit a un redemarrage RabbitMQ avec stockage durable).
     */
    @Bean
    public RabbitTemplateCustomizer persistentNotificationMessages() {
        return (RabbitTemplate rabbitTemplate) -> rabbitTemplate.addBeforePublishPostProcessors(message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            return message;
        });
    }
}
