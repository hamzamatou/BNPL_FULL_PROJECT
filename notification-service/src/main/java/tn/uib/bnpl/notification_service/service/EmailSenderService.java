package tn.uib.bnpl.notification_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;

    public EmailSenderService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled
    ) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
    }

    public void send(String to, String subject, String content, boolean htmlContent) {
        if (!mailEnabled) {
            log.warn("""
                    [app.mail.enabled=false] Message RabbitMQ acquitte sans envoi SMTP — to={}, subject={}. \
                    Activez app.mail.enabled=true et configurez spring.mail.username/password pour envoyer reellement.""",
                    to, subject);
            log.info("Contenu email (apercu local):\n{}", content);
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, htmlContent);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new IllegalStateException("Erreur lors de la construction de l'email", e);
        }
    }
}

