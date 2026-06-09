package tn.uib.bnpl.gestion_demande.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitAuditConfig {

    @Bean
    DirectExchange auditExchange(@Value("${app.rabbit.audit.exchange:audit.exchange}") String exchange) {
        return new DirectExchange(exchange, true, false);
    }
}
