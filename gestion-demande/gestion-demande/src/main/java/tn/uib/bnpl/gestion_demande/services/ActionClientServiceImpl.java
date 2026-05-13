package tn.uib.bnpl.gestion_demande.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tn.uib.bnpl.gestion_demande.classes.ActionClientToken;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.TypeActionClient;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.dto.NotificationEmailRequest;
import tn.uib.bnpl.gestion_demande.repository.ActionClientTokenRepository;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class ActionClientServiceImpl implements ActionClientService {

    private static final Logger log = LoggerFactory.getLogger(ActionClientServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern OTP_SIX_CHIFFRES = Pattern.compile("^\\d{6}$");

    private final ActionClientTokenRepository tokenRepo;
    private final DemandeFinancementRepository demandeRepo;
    private final ClientRemoteService clientRemoteService;
    private final NotificationPublisher notificationPublisher;

    @Value("${app.notifications.async.enabled:false}")
    private boolean asyncNotificationsEnabled;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public ActionClientServiceImpl(
            ActionClientTokenRepository tokenRepo,
            DemandeFinancementRepository demandeRepo,
            ClientRemoteService clientRemoteService,
            NotificationPublisher notificationPublisher) {
        this.tokenRepo = tokenRepo;
        this.demandeRepo = demandeRepo;
        this.clientRemoteService = clientRemoteService;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public String createActionLink(Long demandeId, String emailClient, TypeActionClient typeAction, String frontBaseUrl) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ActionClientToken entity = new ActionClientToken(
                token,
                demandeId,
                typeAction,
                emailClient,
                now.plusHours(2),
                now
        );
        tokenRepo.save(entity);

        String base = frontBaseUrl.endsWith("/")
                ? frontBaseUrl.substring(0, frontBaseUrl.length() - 1)
                : frontBaseUrl;
        String url = base + "/action-client?token=" + token;

        publishConsentNotification(entity.getDemandeId(), emailClient, url);

        return token;
    }

    @Override
    public void sendOtp(String token, String nom, String prenom, String cin) {
        ActionClientToken entity = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(entity.getUsed())) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (entity.getTokenExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Lien expiré");
        }

        DemandeFinancement demande = demandeRepo.findById(entity.getDemandeId())
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));

        Long clientId = demande.getDossierClient().getClientId();

        ClientIdentityDto identity = clientRemoteService.getClientIdentity(clientId);

        boolean match =
                normalize(identity.nom()).equals(normalize(nom)) &&
                normalize(identity.prenom()).equals(normalize(prenom)) &&
                normalize(identity.cin()).equals(normalize(cin));

        if (!match) {
            throw new IllegalArgumentException("Identité client invalide");
        }

        String otp = generateOtp6();

        entity.setOtpHash(sha256(otp));
        entity.setOtpExpiresAt(now.plusMinutes(10));
        entity.setOtpAttempts(0);
        entity.setOtpVerified(false);

        tokenRepo.save(entity);

        publishOtpNotification(entity.getDemandeId(), entity.getEmailClient(), otp);
    }

    @Override
    public void verifyOtp(String token, String otpInput) {
        requireTokenNonVide(token);
        String otpNormalise = normaliserEtValiderOtpSaisi(otpInput);

        ActionClientToken entity = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(entity.getUsed())) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (entity.getTokenExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Lien expiré");
        }
        if (entity.getOtpExpiresAt() == null || entity.getOtpExpiresAt().isBefore(now)) {
            throw new IllegalStateException("OTP expiré");
        }

        int attempts = entity.getOtpAttempts() == null ? 0 : entity.getOtpAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Trop de tentatives OTP");
        }

        String inputHash = sha256(otpNormalise);
        if (!inputHash.equals(entity.getOtpHash())) {
            entity.setOtpAttempts(attempts + 1);
            tokenRepo.save(entity);
            throw new IllegalArgumentException("OTP invalide");
        }

        entity.setOtpVerified(true);
        tokenRepo.save(entity);
    }

    @Override
    public Long validateTokenForConsent(String token) {
        ActionClientToken entity = tokenRepo.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(entity.getUsed())) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (entity.getTokenExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Lien expiré");
        }
        if (!Boolean.TRUE.equals(entity.getOtpVerified())) {
            throw new IllegalStateException("OTP non validé");
        }
        if (entity.getTypeAction() != TypeActionClient.CONSENTEMENT) {
            throw new IllegalStateException("Type action invalide pour consentement");
        }

        entity.setUsed(true);
        entity.setUsedAt(now);
        tokenRepo.save(entity);

        return entity.getDemandeId();
    }

    private static void requireTokenNonVide(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token obligatoire");
        }
    }

    /**
     * Contrôle de saisie du code OTP : non vide après trim, exactement 6 chiffres (même format que {@link #generateOtp6()}).
     */
    private static String normaliserEtValiderOtpSaisi(String otpInput) {
        if (otpInput == null) {
            throw new IllegalArgumentException("Code OTP obligatoire");
        }
        String trimmed = otpInput.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Code OTP obligatoire");
        }
        if (!OTP_SIX_CHIFFRES.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Le code OTP doit contenir exactement 6 chiffres");
        }
        return trimmed;
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private String generateOtp6() {
        int value = RANDOM.nextInt(900_000) + 100_000;
        return String.valueOf(value);
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Erreur hash OTP", e);
        }
    }

    private void publishConsentNotification(Long demandeId, String emailClient, String consentementUrl) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async desactivees: e-mail de consentement non publie (demandeId={})", demandeId);
            return;
        }

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        NotificationEmailRequest event = new NotificationEmailRequest(
                UUID.randomUUID().toString(),
                demande.getReferenceDemande(),
                internalApiKey,
                emailClient,
                "CONSENTEMENT_LINK",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", demande.getReferenceDemande(),
                        "consentementUrl", consentementUrl
                ),
                LocalDateTime.now()
        );
        try {
            notificationPublisher.publishEmail(event);
        } catch (Exception ex) {
            log.error(
                    "Publication RabbitMQ echouee (demandeId={}). Aucun message en file — pas d'envoi mail au redemarrage.",
                    demandeId,
                    ex);
        }
    }

    private void publishOtpNotification(Long demandeId, String emailClient, String otp) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async desactivees: e-mail OTP non publie (demandeId={})", demandeId);
            return;
        }

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        NotificationEmailRequest event = new NotificationEmailRequest(
                UUID.randomUUID().toString(),
                demande.getReferenceDemande(),
                internalApiKey,
                emailClient,
                "OTP_CODE",
                Map.of(
                        "demandeId", demande.getId(),
                        "referenceDemande", demande.getReferenceDemande(),
                        "otp", otp
                ),
                LocalDateTime.now()
        );
        try {
            notificationPublisher.publishEmail(event);
        } catch (Exception ex) {
            log.error(
                    "Publication RabbitMQ echouee pour OTP (demandeId={}). Aucun message en file.",
                    demandeId,
                    ex);
        }
    }
}