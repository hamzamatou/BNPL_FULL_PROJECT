package tn.uib.bnpl.gestion_demande.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * En local, sans {@code spring.mail.username} / SMTP valide, l’envoi échouait et faisait tomber
 * toute la transaction (ex. {@code creation-complete} → 500). Désactiver avec {@code app.mail.enabled=false}
 * ou laisser activé : en cas d’erreur SMTP on loggue sans propager (la demande reste créée).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean mailEnabled;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean mailEnabled) {
        this.mailSender = mailSender;
        this.mailEnabled = mailEnabled;
    }

    public void sendSimple(String to, String subject, String text) {
        if (!mailEnabled) {
            log.warn("[app.mail.enabled=false] Email non envoyé — to={} subject={}", to, subject);
            log.info("Contenu mail (aperçu local) :\n{}", text);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(text);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Échec envoi email vers {} (la demande n’est pas annulée) : {}", to, e.getMessage());
        }
    }
}
