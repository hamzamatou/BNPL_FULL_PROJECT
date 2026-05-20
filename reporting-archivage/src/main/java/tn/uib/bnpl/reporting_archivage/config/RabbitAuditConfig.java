package tn.uib.bnpl.reporting_archivage.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitAuditConfig {

    @Bean
    DirectExchange auditExchange(@Value("${app.rabbit.audit.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }

    @Bean
    Queue auditQueue(@Value("${app.rabbit.audit.queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding auditBinding(Queue auditQueue, DirectExchange auditExchange,
                         @Value("${app.rabbit.audit.routing-key}") String routingKey) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(routingKey);
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
