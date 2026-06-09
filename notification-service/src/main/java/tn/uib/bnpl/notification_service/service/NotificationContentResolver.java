package tn.uib.bnpl.notification_service.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import tn.uib.bnpl.notification_service.dto.NotificationEmailRequest;

@Service
public class NotificationContentResolver {

    private static final Logger log = LoggerFactory.getLogger(NotificationContentResolver.class);

    private final DemandeNotificationGenerator demandeGenerator;
    private final UtilisateurNotificationGenerator utilisateurGenerator;

    @Value("${app.front.base-url:http://localhost:4200}")
    private String defaultFrontBaseUrl;

    public NotificationContentResolver(
            DemandeNotificationGenerator demandeGenerator,
            UtilisateurNotificationGenerator utilisateurGenerator) {
        this.demandeGenerator = demandeGenerator;
        this.utilisateurGenerator = utilisateurGenerator;
    }

    public ResolvedEmail resolve(NotificationEmailRequest event) {
        String template = event.templateCode() == null ? "" : event.templateCode();
        return switch (template.toUpperCase(Locale.ROOT)) {
            case "CONSENTEMENT_LINK" -> resolveConsentement(event);
            case "OTP_CODE" -> resolveDemandeOtp(event);
            case "LOGIN_OTP" -> resolveLoginOtp(event);
            case "ACTIVATION_ACCOUNT" -> resolveActivation(event);
            case "REJET_AUTO_PRESCORING" -> resolveRejetAutoPrescoring(event);
            case "DEMANDE_COMPLEMENTS" -> resolveDemandeComplements(event);
            case "NOUVELLE_DEMANDE_ANALYSTE" -> resolveNouvelleDemandeAnalyste(event);
            case "DECISION_ACCEPTEE" -> resolveDecisionAcceptee(event);
            case "DECISION_REFUSEE" -> resolveDecisionRefusee(event);
            case "DECISION_REFUSEE_PARTIELLE" -> resolveDecisionRefuseePartielle(event);
            default -> new ResolvedEmail(
                    "Notification BNPL - " + safe(event.correlationId()),
                    genericBody(event.data())
            );
        };
    }

    private ResolvedEmail resolveConsentement(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        Long demandeId = toLong(data.get("demandeId"));
        String email = stringVal(data.get("emailClient"), event.to());
        String frontBaseUrl = stringVal(data.get("frontBaseUrl"), defaultFrontBaseUrl);
        String referenceDemande = stringVal(data.get("referenceDemande"), event.correlationId());

        NotificationCredentialService.ConsentGenerated payload = demandeGenerator.generateConsentLink(
                demandeId, email, frontBaseUrl, referenceDemande);
        if (!StringUtils.hasText(payload.consentementUrl())) {
            throw new IllegalStateException("Generation lien consentement impossible pour demandeId=" + demandeId);
        }

        String url = sanitizeConsentUrl(payload.consentementUrl());
        String ref = escapeHtml(payload.referenceDemande());
        String subject = "Lien de consentement BNPL - " + payload.referenceDemande();
        String html = buildConsentHtml(ref, url);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveDemandeOtp(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String token = stringVal(data.get("token"), null);
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token obligatoire pour OTP_CODE");
        }

        NotificationCredentialService.OtpGenerated payload = demandeGenerator.generateDemandeOtp(token);
        if (!StringUtils.hasText(payload.otp())) {
            throw new IllegalStateException("Generation OTP demande impossible");
        }
        String ref = escapeHtml(payload.referenceDemande());
        String otp = escapeHtml(payload.otp());
        String subject = "Code OTP BNPL - " + payload.referenceDemande();
        String html = buildSimpleOtpHtml(
                "Code de verification BNPL",
                "Votre code OTP pour la demande <strong>" + ref + "</strong> :",
                otp);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveLoginOtp(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String email = stringVal(data.get("email"), event.to());
        NotificationCredentialService.LoginOtpGenerated payload = utilisateurGenerator.generateLoginOtp(email);
        if (!StringUtils.hasText(payload.otp())) {
            throw new IllegalStateException("Generation OTP login impossible pour " + email);
        }
        String otp = escapeHtml(payload.otp());
        String subject = "Votre code de verification UIB BNPL";
        String html = buildSimpleOtpHtml(
                "Connexion UIB BNPL",
                "Votre code de verification est :",
                otp);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveActivation(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        Long userId = toLong(data.get("userId"));
        String email = stringVal(data.get("email"), event.to());

        NotificationCredentialService.ActivationGenerated payload =
                utilisateurGenerator.generateActivation(userId, email);
        if (!StringUtils.hasText(payload.activationUrl())) {
            throw new IllegalStateException("Generation activation impossible pour userId=" + userId);
        }
        String url = sanitizeConsentUrl(payload.activationUrl());
        String emailEsc = escapeHtml(payload.email());
        String password = escapeHtml(payload.tempPassword());
        String subject = "Activation compte BNPL";
        String button = url == null ? "" : """
                <p style="margin:24px 0;"><a href="%s" style="display:inline-block;background:#0b5ed7;color:#fff;padding:12px 22px;border-radius:8px;text-decoration:none;font-weight:600;">Activer mon compte</a></p>
                <p style="color:#64748b;font-size:12px;word-break:break-all;">%s</p>
                """.formatted(url, url);
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#0f172a;">Activation compte BNPL</h2>
                <p style="color:#334155;">Bonjour,</p>
                <p style="color:#334155;">Compte : <strong>%s</strong></p>
                <p style="color:#334155;">Mot de passe temporaire : <strong>%s</strong></p>
                %s
                </div></body></html>
                """.formatted(emailEsc, password, button);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveRejetAutoPrescoring(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomClient"), ""));
        String nom = escapeHtml(stringVal(data.get("nomClient"), ""));
        int score = toInt(data.get("score"), 0);
        String zoneCode = escapeHtml(stringVal(data.get("zoneCode"), "rouge"));
        double pd = toDouble(data.get("probabiliteDefaut"), 0.0);
        StringBuilder explicationsHtml = new StringBuilder();
        Object rawExplications = data.get("explications");
        if (rawExplications instanceof Iterable<?> iterable) {
            for (Object line : iterable) {
                if (line != null && !line.toString().isBlank()) {
                    explicationsHtml.append("<li style=\"margin-bottom:8px;color:#334155;\">")
                            .append(escapeHtml(line.toString()))
                            .append("</li>");
                }
            }
        }
        if (explicationsHtml.isEmpty()) {
            explicationsHtml.append("<li style=\"color:#64748b;\">Explications indisponibles.</li>");
        }
        String subject = "Decision sur votre demande BNPL - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#b91c1c;">Demande non recevable automatiquement</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;">Votre demande <strong>%s</strong> n'a pas pu etre orientee vers une analyse bancaire : le profil de risque est en <strong style="color:#b91c1c;">zone %s</strong> (score %d, probabilite de defaut %.1f%%).</p>
                <p style="color:#334155;font-weight:600;margin-top:20px;">Elements d'explication (modele prescoring) :</p>
                <ul style="padding-left:20px;margin-top:8px;">%s</ul>
                <p style="color:#64748b;font-size:13px;margin-top:24px;">Cette decision automatique ne remplace pas un examen approfondi par un conseiller. Pour toute question, contactez votre point de vente ou votre banque.</p>
                </div></body></html>
                """.formatted(prenom, nom, ref, zoneCode, score, pd, explicationsHtml);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveDemandeComplements(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomClient"), ""));
        String nom = escapeHtml(stringVal(data.get("nomClient"), ""));
        String commentaire = escapeHtml(stringVal(data.get("commentaire"), "Merci de nous transmettre les pieces demandees."));
        String nomBanque = escapeHtml(stringVal(data.get("nomBanque"), "Banque partenaire"));
        String subject = "Pieces complementaires demandees - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#0f172a;">Informations complementaires requises</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;">Votre demande <strong>%s</strong> est analysee par <strong>%s</strong>.</p>
                <p style="color:#334155;background:#f8fafc;border-left:4px solid #0b5ed7;padding:12px 16px;">%s</p>
                <p style="color:#64748b;font-size:13px;margin-top:24px;">Merci de contacter votre point de vente pour transmettre les documents demandes.</p>
                </div></body></html>
                """.formatted(prenom, nom, ref, nomBanque, commentaire);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveDecisionAcceptee(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomClient"), ""));
        String nom = escapeHtml(stringVal(data.get("nomClient"), ""));
        String nomBanque = escapeHtml(stringVal(data.get("nomBanque"), "Banque partenaire"));
        String subject = "Demande acceptee - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#15803d;">Financement accepte</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;">Votre demande <strong>%s</strong> a ete <strong style="color:#15803d;">acceptee</strong> par <strong>%s</strong>.</p>
                <p style="color:#64748b;font-size:13px;margin-top:24px;">Vous serez contacte pour la suite du dossier.</p>
                </div></body></html>
                """.formatted(prenom, nom, ref, nomBanque);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveDecisionRefusee(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomClient"), ""));
        String nom = escapeHtml(stringVal(data.get("nomClient"), ""));
        String motif = escapeHtml(stringVal(data.get("motifRefus"), "Decision defavorable"));
        String nomBanque = escapeHtml(stringVal(data.get("nomBanque"), "Banque partenaire"));
        String subject = "Demande refusee - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#b91c1c;">Demande refusee</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;">Votre demande <strong>%s</strong> a ete <strong style="color:#b91c1c;">refusee</strong> par <strong>%s</strong>.</p>
                <p style="color:#334155;background:#fef2f2;border-left:4px solid #b91c1c;padding:12px 16px;">Motif : %s</p>
                </div></body></html>
                """.formatted(prenom, nom, ref, nomBanque, motif);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveDecisionRefuseePartielle(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomClient"), ""));
        String nom = escapeHtml(stringVal(data.get("nomClient"), ""));
        String motif = escapeHtml(stringVal(data.get("motifRefus"), "Decision defavorable"));
        String nomBanque = escapeHtml(stringVal(data.get("nomBanque"), "Banque partenaire"));
        String subject = "Decision bancaire - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#b45309;">Decision bancaire</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;"><strong>%s</strong> a refuse votre demande <strong>%s</strong>.</p>
                <p style="color:#334155;background:#fffbeb;border-left:4px solid #b45309;padding:12px 16px;">Motif : %s</p>
                <p style="color:#64748b;font-size:13px;margin-top:24px;">Votre dossier reste ouvert et peut etre examine par une autre banque partenaire.</p>
                </div></body></html>
                """.formatted(prenom, nom, nomBanque, ref, motif);
        return new ResolvedEmail(subject, html);
    }

    private ResolvedEmail resolveNouvelleDemandeAnalyste(NotificationEmailRequest event) {
        Map<String, Object> data = requireData(event);
        String ref = escapeHtml(stringVal(data.get("referenceDemande"), event.correlationId()));
        String prenom = escapeHtml(stringVal(data.get("prenomAnalyste"), ""));
        String nom = escapeHtml(stringVal(data.get("nomAnalyste"), ""));
        String nomBanque = escapeHtml(stringVal(data.get("nomBanque"), "Banque partenaire"));
        String montant = escapeHtml(stringVal(data.get("montant"), ""));
        String duree = escapeHtml(String.valueOf(toInt(data.get("dureeMois"), 0)));
        String frontBaseUrl = stringVal(data.get("frontBaseUrl"), defaultFrontBaseUrl);
        String portalUrl = escapeHtml(frontBaseUrl + "/banque/demandes");
        String subject = "Nouvelle demande a instruire - " + stringVal(data.get("referenceDemande"), event.correlationId());
        String html = """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#0f172a;">Nouvelle demande BNPL</h2>
                <p style="color:#334155;">Bonjour %s %s,</p>
                <p style="color:#334155;">Une nouvelle demande <strong>%s</strong> vous a ete assignee pour <strong>%s</strong>.</p>
                <p style="color:#334155;">Montant : <strong>%s TND</strong> — Duree : <strong>%s mois</strong></p>
                <p style="margin-top:24px;"><a href="%s" style="display:inline-block;background:#0b5ed7;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;">Ouvrir le portail banque</a></p>
                </div></body></html>
                """.formatted(prenom, nom, ref, nomBanque, montant, duree, portalUrl);
        return new ResolvedEmail(subject, html);
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double toDouble(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String buildSimpleOtpHtml(String title, String instruction, String otp) {
        return """
                <html><body style="margin:0;padding:32px 24px;background:#fff;font-family:Arial,sans-serif;">
                <h2 style="margin:0 0 16px;font-size:18px;font-weight:700;color:#0f172a;text-align:left;">%s</h2>
                <p style="margin:0 0 28px;color:#334155;font-size:15px;text-align:left;">%s</p>
                <p style="margin:0 0 12px;font-size:32px;font-weight:700;letter-spacing:4px;color:#d3121f;text-align:center;">%s</p>
                <p style="margin:0;color:#64748b;font-size:13px;text-align:center;">Valide 5 minutes.</p>
                </body></html>
                """.formatted(title, instruction, otp);
    }

    private String buildConsentHtml(String referenceDemande, String url) {
        String buttonOrFallback = url == null
                ? "<p style=\"color:#b91c1c;\">Lien indisponible. Contactez le support.</p>"
                : """
                  <p style="margin:28px 0;"><a href="%s" target="_blank" rel="noopener noreferrer" style="display:inline-block;background:#0b5ed7;color:#fff;padding:12px 22px;border-radius:8px;text-decoration:none;font-weight:600;">Confirmer mon consentement</a></p>
                  <p style="color:#64748b;font-size:12px;word-break:break-all;">%s</p>
                  """.formatted(url, url);
        return """
                <html><body style="margin:0;padding:0;background:#f5f7fb;font-family:Arial,sans-serif;">
                <div style="max-width:640px;margin:24px auto;padding:24px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;">
                <h2 style="color:#0f172a;">Validation de consentement BNPL</h2>
                <p style="color:#334155;">Demande <strong>%s</strong> — lien valide 2 heures.</p>
                %s
                </div></body></html>
                """.formatted(referenceDemande, buttonOrFallback);
    }

    private static String genericBody(Map<String, Object> data) {
        return """
                <html><body><p>Notification BNPL</p><pre>%s</pre></body></html>
                """.formatted(escapeHtml(String.valueOf(data)));
    }

    private static Map<String, Object> requireData(NotificationEmailRequest event) {
        if (event.data() == null || event.data().isEmpty()) {
            throw new IllegalArgumentException("Donnees notification manquantes pour " + event.templateCode());
        }
        return event.data();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Identifiant numerique manquant");
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private static String stringVal(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private String sanitizeConsentUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
                return null;
            }
            if ("https".equalsIgnoreCase(scheme)) {
                return uri.toASCIIString();
            }
            if ("http".equalsIgnoreCase(scheme) && isLocalDevHost(host)) {
                return uri.toASCIIString();
            }
            log.warn("URL refusee: {}", url);
            return null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isLocalDevHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || "127.0.0.1".equals(h) || "::1".equals(h);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record ResolvedEmail(String subject, String html) {}
}
