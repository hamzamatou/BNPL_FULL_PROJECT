package tn.uib.bnpl.notification_service.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

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
    private final String internalApiKey;

    public NotificationConsumer(
            EmailSenderService emailSenderService,
            @Value("${internal.api.key}") String internalApiKey
    ) {
        this.emailSenderService = emailSenderService;
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
            throw new IllegalArgumentException("Clé interne invalide");
        }

        String subject = buildSubject(event.templateCode(), event.correlationId());
        String content = buildContent(event);

        emailSenderService.send(event.to(), subject, content, true);
        log.info("Notification traitée: eventId={} correlationId={} to={}",
                event.eventId(), event.correlationId(), event.to());
    }

    private String buildSubject(String templateCode, String correlationId) {
        if ("CONSENTEMENT_LINK".equalsIgnoreCase(templateCode)) {
            return "Lien de consentement BNPL - " + (correlationId == null ? "" : correlationId);
        }
        if ("OTP_CODE".equalsIgnoreCase(templateCode)) {
            return "Code OTP BNPL - " + (correlationId == null ? "" : correlationId);
        }
        return "Notification BNPL - " + (correlationId == null ? "" : correlationId);
    }

    private String buildContent(NotificationEmailRequest event) {
        Map<String, Object> data = event.data();
        if (data == null || data.isEmpty()) {
            return """
                    <html>
                      <body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                        <div style="max-width:640px;margin:24px auto;padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                          <h2 style="margin-top:0;color:#0f172a;">Notification BNPL</h2>
                          <p style="color:#334155;line-height:1.6;">Bonjour,</p>
                          <p style="color:#334155;line-height:1.6;">Vous avez une nouvelle notification BNPL.</p>
                          <p style="color:#64748b;font-size:12px;margin-top:24px;">Cet e-mail est genere automatiquement par le service BNPL.</p>
                        </div>
                      </body>
                    </html>
                    """.strip();
        }

        if ("CONSENTEMENT_LINK".equalsIgnoreCase(event.templateCode())) {
            String url = sanitizeConsentUrl(data.get("consentementUrl"));
            Object ref = data.get("referenceDemande");
            String referenceDemande = escapeHtml(ref == null ? "-" : ref.toString());
            String buttonOrFallback = url == null
                    ? "<p style=\"color:#b91c1c;line-height:1.6;\">Le lien de consentement est indisponible pour des raisons de securite. Merci de contacter le support.</p>"
                    : """
                        <p style="margin:28px 0;">
                          <a href="%s" target="_blank" rel="noopener noreferrer" style="display:inline-block;background:#0b5ed7;color:#ffffff;text-decoration:none;padding:12px 22px;border-radius:8px;font-weight:600;">
                            Confirmer mon consentement
                          </a>
                        </p>
                        <p style="color:#64748b;font-size:12px;line-height:1.5;">
                          Si le bouton ne fonctionne pas, copiez ce lien dans votre navigateur :
                          <br/>
                          <a href="%s" target="_blank" rel="noopener noreferrer" style="color:#0b5ed7;word-break:break-all;">%s</a>
                        </p>
                        """.formatted(url, url, url);
            return """
                    <html>
                      <body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                        <div style="max-width:640px;margin:24px auto;padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                          <h2 style="margin-top:0;color:#0f172a;">Validation de consentement BNPL</h2>
                          <p style="color:#334155;line-height:1.6;">Bonjour,</p>
                          <p style="color:#334155;line-height:1.6;">
                            Veuillez confirmer votre consentement pour la demande BNPL
                            <strong>%s</strong>.
                          </p>
                          %s
                          <p style="color:#334155;line-height:1.6;">Merci.</p>
                          <p style="color:#64748b;font-size:12px;margin-top:24px;">
                            En production, utilisez HTTPS. En developpement, http://localhost est accepte.</p>
                        </div>
                      </body>
                    </html>
                    """.formatted(referenceDemande, buttonOrFallback).strip();
        }

        if ("OTP_CODE".equalsIgnoreCase(event.templateCode())) {
            Object otp = data.get("otp");
            Object ref = data.get("referenceDemande");
            String referenceDemande = escapeHtml(ref == null ? "-" : ref.toString());
            String otpText = escapeHtml(otp == null ? "-" : otp.toString());
            return """
                    <html>
                      <body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                        <div style="max-width:640px;margin:24px auto;padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                          <h2 style="margin-top:0;color:#0f172a;">Code de verification BNPL</h2>
                          <p style="color:#334155;line-height:1.6;">Bonjour,</p>
                          <p style="color:#334155;line-height:1.6;">
                            Votre code OTP pour la demande <strong>%s</strong> est :
                          </p>
                          <p style="font-size:28px;letter-spacing:6px;font-weight:700;color:#0f172a;margin:20px 0;">%s</p>
                          <p style="color:#64748b;font-size:13px;line-height:1.5;">Ce code est valide 10 minutes.</p>
                        </div>
                      </body>
                    </html>
                    """.formatted(referenceDemande, otpText).strip();
        }

        return """
                <html>
                  <body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                    <div style="max-width:640px;margin:24px auto;padding:24px;background:#ffffff;border:1px solid #e5e7eb;border-radius:12px;">
                      <h2 style="margin-top:0;color:#0f172a;">Notification BNPL</h2>
                      <p style="color:#334155;line-height:1.6;">Bonjour,</p>
                      <p style="color:#334155;line-height:1.6;">Notification recue.</p>
                      <pre style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px;color:#1e293b;white-space:pre-wrap;">%s</pre>
                    </div>
                  </body>
                </html>
                """.formatted(escapeHtml(String.valueOf(data))).strip();
    }

    /**
     * Accepte HTTPS partout ; en developpement, HTTP uniquement pour localhost / 127.0.0.1 / [::1].
     */
    private String sanitizeConsentUrl(Object rawUrl) {
        if (rawUrl == null) {
            return null;
        }
        String url = rawUrl.toString().trim();
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                log.warn("Lien de consentement refuse (scheme ou host vide): {}", url);
                return null;
            }
            if ("https".equalsIgnoreCase(scheme)) {
                return uri.toASCIIString();
            }
            if ("http".equalsIgnoreCase(scheme) && isLocalDevHost(host)) {
                return uri.toASCIIString();
            }
            log.warn("Lien de consentement refuse (scheme/host non autorise): {}", url);
            return null;
        } catch (URISyntaxException e) {
            log.warn("Lien de consentement invalide: {}", url);
            return null;
        }
    }

    private static boolean isLocalDevHost(String host) {
        if (host == null) {
            return false;
        }
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h) || "[::1]".equals(h);
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}

