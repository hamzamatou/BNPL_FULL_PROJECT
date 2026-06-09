package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.TypeActionClient;
import tn.uib.bnpl.gestion_demande.client.NotificationServiceClient;
import tn.uib.bnpl.gestion_demande.dto.NotificationEmailRequest;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class NotificationBrokerTest {

    @Mock
    private DemandeFinancementRepository demandeRepo;
    @Mock
    private ClientRemoteService clientRemoteService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private NotificationServiceClient notificationClient;
    @Mock
    private DemandeHistoriqueService historiqueService;

    private NotificationPublisher notificationPublisher;
    private ActionClientServiceImpl actionClientService;

    @BeforeEach
    void setUp() {
        notificationPublisher = new NotificationPublisher(
                rabbitTemplate,
                new Jackson2JsonMessageConverter(),
                new ObjectMapper());
        ReflectionTestUtils.setField(notificationPublisher, "exchangeName", "notification.exchange");
        ReflectionTestUtils.setField(notificationPublisher, "routingKey", "notification.email.request");
        ReflectionTestUtils.setField(notificationPublisher, "internalApiKey", "test-internal-key");

        actionClientService = new ActionClientServiceImpl(
                demandeRepo,
                clientRemoteService,
                notificationPublisher,
                notificationClient,
                historiqueService);
    }

    @Nested
    @Order(1)
    class NotificationSansBroker {

        @BeforeEach
        void disableBroker() {
            ReflectionTestUtils.setField(notificationPublisher, "asyncNotificationsEnabled", false);
        }

        @Test
        void notificationsDesactivees_nePublieRien() {
            DemandeFinancement demande = demandeEnAttente(7L, "DEM-007", "CREE");
            when(demandeRepo.findById(7L)).thenReturn(Optional.of(demande));

            actionClientService.requestConsentementEmail(
                    7L,
                    "client@example.com",
                    TypeActionClient.CONSENTEMENT,
                    "http://localhost:4200");

            verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(Object.class));
        }
    }

    @Nested
    @Order(2)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class NotificationAvecBroker {

        @BeforeEach
        void enableBroker() {
            ReflectionTestUtils.setField(notificationPublisher, "asyncNotificationsEnabled", true);
        }

        @Test
        @Order(1)
        void publicationMessage_succes() {
            DemandeFinancement demande = demandeEnAttente(7L, "DEM-007", "CREE");
            when(demandeRepo.findById(7L)).thenReturn(Optional.of(demande));

            actionClientService.requestConsentementEmail(
                    7L,
                    "client@example.com",
                    TypeActionClient.CONSENTEMENT,
                    "http://localhost:4200");

            ArgumentCaptor<NotificationEmailRequest> captor =
                    ArgumentCaptor.forClass(NotificationEmailRequest.class);
            verify(rabbitTemplate).convertAndSend(
                    org.mockito.ArgumentMatchers.eq("notification.exchange"),
                    org.mockito.ArgumentMatchers.eq("notification.email.request"),
                    captor.capture());

            NotificationEmailRequest event = captor.getValue();
            assertThat(event.to()).isEqualTo("client@example.com");
            assertThat(event.templateCode()).isEqualTo("CONSENTEMENT_LINK");
            assertThat(event.internalApiKey()).isEqualTo("test-internal-key");
            assertThat(event.correlationId()).isEqualTo("DEM-007");
            assertThat(event.data())
                    .containsEntry("demandeId", 7L)
                    .containsEntry("referenceDemande", "DEM-007")
                    .containsEntry("emailClient", "client@example.com")
                    .containsEntry("frontBaseUrl", "http://localhost:4200")
                    .containsEntry("typeAction", "CONSENTEMENT");

            assertThat(demande.getStatut()).isEqualTo("EN_ATTENTE_CONSENTEMENT");
            verify(demandeRepo).save(demande);
        }

        @Test
        @Order(2)
        void publicationMessage_echec() {
            DemandeFinancement demande = demandeEnAttente(8L, "DEM-008", "CREE");
            when(demandeRepo.findById(8L)).thenReturn(Optional.of(demande));
            doThrow(new RuntimeException("broker down"))
                    .when(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));

            assertThatThrownBy(() -> actionClientService.requestConsentementEmail(
                    8L,
                    "client@example.com",
                    TypeActionClient.CONSENTEMENT,
                    "http://localhost:4200"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Impossible de publier la notification consentement");

            assertThat(demande.getStatut()).isEqualTo("CREE");
            verify(demandeRepo, never()).save(any(DemandeFinancement.class));
        }

        private DemandeFinancement demandeEnAttente(Long id, String ref, String statut) {
            DemandeFinancement demande = new DemandeFinancement();
            ReflectionTestUtils.setField(demande, "id", id);
            demande.setReferenceDemande(ref);
            demande.setStatut(statut);
            return demande;
        }
    }

    private static DemandeFinancement demandeEnAttente(Long id, String ref, String statut) {
        DemandeFinancement demande = new DemandeFinancement();
        ReflectionTestUtils.setField(demande, "id", id);
        demande.setReferenceDemande(ref);
        demande.setStatut(statut);
        return demande;
    }
}
