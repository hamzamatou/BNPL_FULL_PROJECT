package tn.uib.bnpl.gestion_utilisateur.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitNotificationConfig {

    @Value("${app.rabbit.notification.exchange:notification.exchange}")
    private String exchangeName;

    @Value("${app.rabbit.notification.queue:notification.email.send}")
    private String queueName;

    @Value("${app.rabbit.notification.routing-key:notification.email.request}")
    private String routingKey;

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public Queue notificationEmailQueue() {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding notificationEmailBinding(Queue notificationEmailQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationEmailQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
