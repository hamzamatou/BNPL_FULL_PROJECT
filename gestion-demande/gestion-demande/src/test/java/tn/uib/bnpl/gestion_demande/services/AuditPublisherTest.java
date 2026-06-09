package tn.uib.bnpl.gestion_demande.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import tn.uib.bnpl.gestion_demande.dto.audit.AuditEventRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditPublisherTest {

    private static final String EXCHANGE = "audit.exchange";
    private static final String ROUTING_KEY = "audit.event";

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AuditPublisher auditPublisher;

    @BeforeEach
    void setUp() {
        auditPublisher = new AuditPublisher(rabbitTemplate, new Jackson2JsonMessageConverter());
        ReflectionTestUtils.setField(auditPublisher, "exchangeName", EXCHANGE);
        ReflectionTestUtils.setField(auditPublisher, "routingKey", ROUTING_KEY);
        ReflectionTestUtils.setField(auditPublisher, "enabled", true);
    }

    @Test
    void publier_envoieSurExchangeAudit() {
        AuditEventRequest request = new AuditEventRequest(
                "ACTION_DEMANDE",
                "corr-1",
                null,
                null,
                null
        );

        auditPublisher.publier(request);

        ArgumentCaptor<AuditEventRequest> captor = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("ACTION_DEMANDE");
        assertThat(captor.getValue().correlationId()).isEqualTo("corr-1");
    }
}
