package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import tn.uib.bnpl.gestion_demande.dto.NotificationEmailRequest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    private static final String EXCHANGE = "notification.exchange";
    private static final String ROUTING_KEY = "notification.email.request";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private Jackson2JsonMessageConverter messageConverter;

    private NotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        notificationPublisher = new NotificationPublisher(rabbitTemplate, messageConverter, new ObjectMapper());
        ReflectionTestUtils.setField(notificationPublisher, "exchangeName", EXCHANGE);
        ReflectionTestUtils.setField(notificationPublisher, "routingKey", ROUTING_KEY);
        ReflectionTestUtils.setField(notificationPublisher, "asyncNotificationsEnabled", true);
        ReflectionTestUtils.setField(notificationPublisher, "internalApiKey", "internal-key");
    }

    @Test
    void publishEmail_envoieAuBrokerAvecExchangeEtRoutingKey() {
        NotificationEmailRequest payload = new NotificationEmailRequest(
                "evt-1",
                "DEM-100",
                "internal-key",
                "client@example.com",
                "CONSENTEMENT_LINK",
                Map.of("demandeId", 100L),
                LocalDateTime.now()
        );

        notificationPublisher.publishEmail(payload);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, ROUTING_KEY, payload);
    }

    @Test
    void publishEmail_conserveContenuConsentement() {
        NotificationEmailRequest payload = new NotificationEmailRequest(
                "evt-consent",
                "DEM-42",
                "dev-internal-key",
                "client@bnpl.tn",
                "CONSENTEMENT_LINK",
                Map.of(
                        "demandeId", 42L,
                        "referenceDemande", "DEM-42",
                        "emailClient", "client@bnpl.tn",
                        "frontBaseUrl", "http://localhost:4200",
                        "typeAction", "CONSENTEMENT"
                ),
                LocalDateTime.now()
        );

        notificationPublisher.publishEmail(payload);

        ArgumentCaptor<NotificationEmailRequest> captor =
                ArgumentCaptor.forClass(NotificationEmailRequest.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(EXCHANGE),
                org.mockito.ArgumentMatchers.eq(ROUTING_KEY),
                captor.capture());

        NotificationEmailRequest sent = captor.getValue();
        assertThat(sent.templateCode()).isEqualTo("CONSENTEMENT_LINK");
        assertThat(sent.to()).isEqualTo("client@bnpl.tn");
        assertThat(sent.data()).containsEntry("demandeId", 42L);
    }
}
