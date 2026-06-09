package tn.uib.bnpl.notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tn.uib.bnpl.notification_service.dto.NotificationEmailRequest;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final EmailSenderService emailSenderService;
    private final NotificationContentResolver contentResolver;
    private final String internalApiKey;

    public NotificationConsumer(
            EmailSenderService emailSenderService,
            NotificationContentResolver contentResolver,
            @Value("${internal.api.key}") String internalApiKey
    ) {
        this.emailSenderService = emailSenderService;
        this.contentResolver = contentResolver;
        this.internalApiKey = internalApiKey;
    }

    @RabbitListener(queues = "${app.rabbit.notification.queue:notification.email.send}")
    public void consume(NotificationEmailRequest event) {
        if (event == null) {
            throw new IllegalArgumentException("Message notification vide");
        }
        if (!StringUtils.hasText(event.to())) {
            throw new IllegalArgumentException("Destinataire email manquant");
        }
        if (!StringUtils.hasText(event.internalApiKey()) || !internalApiKey.equals(event.internalApiKey())) {
            throw new IllegalArgumentException("Cle interne invalide");
        }

        NotificationContentResolver.ResolvedEmail resolved = contentResolver.resolve(event);
        emailSenderService.send(event.to(), resolved.subject(), resolved.html(), true);
        log.info("Notification traitee (OTP/lien generes en base notification): eventId={} template={} to={}",
                event.eventId(), event.templateCode(), event.to());
    }
}
