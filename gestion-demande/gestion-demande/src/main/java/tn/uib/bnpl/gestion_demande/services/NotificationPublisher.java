package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.dto.NotificationEmailRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Point d'entrée unique pour toute publication RabbitMQ vers notification-service.
 */
@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbit.notification.exchange:notification.exchange}")
    private String exchangeName;

    @Value("${app.rabbit.notification.routing-key:notification.email.request}")
    private String routingKey;

    @Value("${app.notifications.async.enabled:true}")
    private boolean asyncNotificationsEnabled;

    @Value("${internal.api.key}")
    private String internalApiKey;

    @Value("${app.front.base-url:http://localhost:4200}")
    private String frontBaseUrl;

    public NotificationPublisher(
            RabbitTemplate rabbitTemplate,
            Jackson2JsonMessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitTemplate.setMessageConverter(messageConverter);
        this.objectMapper = objectMapper;
    }

    public void publishEmail(NotificationEmailRequest payload) {
        rabbitTemplate.convertAndSend(exchangeName, routingKey, payload);
        log.info("Notification email publiée: eventId={} correlationId={} to={}",
                payload.eventId(), payload.correlationId(), payload.to());
    }

    public void publishDemandeComplements(DemandeFinancement demande,
                                          ClientIdentityDto client,
                                          String commentaire,
                                          String nomBanque) {
        if (!canPublish(client)) {
            log.warn("Notification compléments ignorée (demandeId={})", demande.getId());
            return;
        }
        publishForDemande(
                demande,
                client.email(),
                "DEMANDE_COMPLEMENTS",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", safe(demande.getReferenceDemande()),
                        "nomClient", safe(client.nom()),
                        "prenomClient", safe(client.prenom()),
                        "commentaire", safe(commentaire),
                        "nomBanque", safe(nomBanque)
                )
        );
    }

    public void publishNouvelleDemandeAnalyste(DemandeFinancement demande,
                                               String analysteEmail,
                                               String nomAnalyste,
                                               String prenomAnalyste,
                                               String nomBanque,
                                               String codeBanque) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async desactivees — e-mail nouvelle demande analyste non publie (demandeId={})",
                    demande.getId());
            return;
        }
        if (analysteEmail == null || analysteEmail.isBlank()) {
            log.warn("E-mail analyste indisponible — notification routage ignoree (demandeId={})", demande.getId());
            return;
        }
        publishForDemande(
                demande,
                analysteEmail.trim(),
                "NOUVELLE_DEMANDE_ANALYSTE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", safe(demande.getReferenceDemande()),
                        "nomAnalyste", safe(nomAnalyste),
                        "prenomAnalyste", safe(prenomAnalyste),
                        "nomBanque", safe(nomBanque),
                        "codeBanque", safe(codeBanque),
                        "montant", demande.getMontant() != null ? demande.getMontant().toPlainString() : "",
                        "dureeMois", demande.getDureeMois() != null ? demande.getDureeMois() : 0,
                        "frontBaseUrl", safe(frontBaseUrl)
                )
        );
    }

    public void publishDecisionAcceptee(DemandeFinancement demande,
                                        ClientIdentityDto client,
                                        String nomBanque) {
        if (!canPublish(client)) {
            return;
        }
        publishForDemande(
                demande,
                client.email(),
                "DECISION_ACCEPTEE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", safe(demande.getReferenceDemande()),
                        "nomClient", safe(client.nom()),
                        "prenomClient", safe(client.prenom()),
                        "nomBanque", safe(nomBanque)
                )
        );
    }

    public void publishDecisionRefusee(DemandeFinancement demande,
                                       ClientIdentityDto client,
                                       String motif,
                                       String nomBanque) {
        if (!canPublish(client)) {
            return;
        }
        publishForDemande(
                demande,
                client.email(),
                "DECISION_REFUSEE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", safe(demande.getReferenceDemande()),
                        "nomClient", safe(client.nom()),
                        "prenomClient", safe(client.prenom()),
                        "motifRefus", safe(motif),
                        "nomBanque", safe(nomBanque)
                )
        );
    }

    public void publishDecisionRefuseePartielle(DemandeFinancement demande,
                                                ClientIdentityDto client,
                                                String motif,
                                                String nomBanque) {
        if (!canPublish(client)) {
            return;
        }
        publishForDemande(
                demande,
                client.email(),
                "DECISION_REFUSEE_PARTIELLE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", safe(demande.getReferenceDemande()),
                        "nomClient", safe(client.nom()),
                        "prenomClient", safe(client.prenom()),
                        "motifRefus", safe(motif),
                        "nomBanque", safe(nomBanque)
                )
        );
    }

    public void publishRejetAutoPrescoring(DemandeFinancement demande,
                                           PrescoringScore score,
                                           ClientIdentityDto client) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async désactivées — e-mail rejet auto non publié (demandeId={})",
                    demande.getId());
            return;
        }
        if (!canPublish(client)) {
            log.warn("E-mail client indisponible — rejet auto sans notification (demandeId={})", demande.getId());
            return;
        }

        List<String> explications = parseExplications(score != null ? score.getExplicationsJson() : null);
        int scoreValue = score != null ? score.getScore() : 0;
        String zoneCode = score != null && score.getZoneCode() != null ? score.getZoneCode() : "rouge";
        double pd = score != null ? score.getProbabiliteDefaut() : 0.0;

        publishForDemande(
                demande,
                client.email(),
                "REJET_AUTO_PRESCORING",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", demande.getReferenceDemande(),
                        "nomClient", safe(client.nom()),
                        "prenomClient", safe(client.prenom()),
                        "score", scoreValue,
                        "zoneCode", zoneCode,
                        "probabiliteDefaut", pd,
                        "explications", explications
                )
        );
        log.info("Rejet auto prescoring — notification publiée (demandeId={} to={})",
                demande.getId(), client.email());
    }

    public void publishConsentementLink(DemandeFinancement demande,
                                        String emailClient,
                                        String frontBaseUrl,
                                        String typeAction) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async désactivées — e-mail consentement non publié (demandeId={})",
                    demande.getId());
            return;
        }
        publishForDemande(
                demande,
                emailClient,
                "CONSENTEMENT_LINK",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", demande.getReferenceDemande(),
                        "emailClient", emailClient,
                        "frontBaseUrl", frontBaseUrl,
                        "typeAction", typeAction
                )
        );
    }

    public void publishOtpCode(DemandeFinancement demande,
                               String emailClient,
                               String token,
                               String nom,
                               String prenom,
                               String cin) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async désactivées — e-mail OTP non publié (demandeId={})",
                    demande.getId());
            return;
        }
        publishForDemande(
                demande,
                emailClient,
                "OTP_CODE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", demande.getReferenceDemande(),
                        "token", token,
                        "nom", nom,
                        "prenom", prenom,
                        "cin", cin
                )
        );
    }

    private void publishForDemande(DemandeFinancement demande,
                                   String to,
                                   String template,
                                   Map<String, Object> data) {
        NotificationEmailRequest event = new NotificationEmailRequest(
                UUID.randomUUID().toString(),
                demande.getReferenceDemande(),
                internalApiKey,
                to,
                template,
                data,
                LocalDateTime.now()
        );
        publishEmail(event);
        log.info("Notification publiée template={} demandeId={} to={}", template, demande.getId(), to);
    }

    private boolean canPublish(ClientIdentityDto client) {
        return asyncNotificationsEnabled
                && client != null
                && client.email() != null
                && !client.email().isBlank();
    }

    private List<String> parseExplications(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            log.debug("Explications prescoring non parsées : {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
