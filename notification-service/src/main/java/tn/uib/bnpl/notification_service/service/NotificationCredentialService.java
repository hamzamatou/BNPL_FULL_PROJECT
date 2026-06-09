package tn.uib.bnpl.notification_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tn.uib.bnpl.notification_service.persistence.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationCredentialService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Pattern OTP_SIX = Pattern.compile("^\\d{6}$");

    private final NotificationActionLinkRepository linkRepository;
    private final NotificationOtpRepository otpRepository;

    @Value("${app.front.base-url:http://localhost:4200}")
    private String defaultFrontBaseUrl;

    public NotificationCredentialService(
            NotificationActionLinkRepository linkRepository,
            NotificationOtpRepository otpRepository) {
        this.linkRepository = linkRepository;
        this.otpRepository = otpRepository;
    }

    @Transactional
    public ConsentGenerated createConsentLink(
            Long demandeId,
            String emailClient,
            String frontBaseUrl,
            String referenceDemande) {
        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        NotificationActionLink link = new NotificationActionLink();
        link.setToken(token);
        link.setSubjectRef(demandeId);
        link.setEmail(emailClient);
        link.setLinkType(ActionLinkType.CONSENTEMENT);
        link.setReferenceLabel(referenceDemande);
        link.setExpiresAt(now.plusHours(2));
        link.setUsed(false);
        link.setOtpVerified(false);
        link.setCreatedAt(now);
        linkRepository.save(link);

        String url = normalizeBaseUrl(frontBaseUrl) + "/action-client?token=" + token;
        return new ConsentGenerated(url, referenceDemande, token);
    }

    @Transactional
    public OtpGenerated createDemandeOtp(String linkToken) {
        NotificationActionLink link = linkRepository.findByToken(linkToken)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();
        validateLinkActive(link, now);

        String otp = generateOtp6();
        NotificationOtp otpEntity = new NotificationOtp();
        otpEntity.setEmail(link.getEmail());
        otpEntity.setContext(OtpContext.DEMANDE_OTP);
        otpEntity.setCodeHash(sha256(otp));
        otpEntity.setExpiresAt(now.plusMinutes(5));
        otpEntity.setUsed(false);
        otpEntity.setAttempts(0);
        otpEntity.setLinkToken(linkToken);
        otpEntity.setCreatedAt(now);
        otpRepository.save(otpEntity);

        link.setOtpVerified(false);
        linkRepository.save(link);

        return new OtpGenerated(otp, link.getEmail(), link.getReferenceLabel());
    }

    @Transactional
    public LoginOtpGenerated createLoginOtp(String email) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Email obligatoire");
        }
        String normalized = email.trim();
        LocalDateTime now = LocalDateTime.now();
        String otp = generateOtp6();

        NotificationOtp entity = new NotificationOtp();
        entity.setEmail(normalized);
        entity.setContext(OtpContext.LOGIN_OTP);
        entity.setCodeHash(sha256(otp));
        entity.setExpiresAt(now.plusMinutes(5));
        entity.setUsed(false);
        entity.setAttempts(0);
        entity.setCreatedAt(now);
        otpRepository.save(entity);

        return new LoginOtpGenerated(normalized, otp);
    }

    @Transactional
    public ActivationGenerated createActivationLink(Long userId, String email) {
        String token = UUID.randomUUID().toString();
        String tempPassword = "Bnpl@" + (RANDOM.nextInt(90000) + 10000);
        LocalDateTime now = LocalDateTime.now();

        NotificationActionLink link = new NotificationActionLink();
        link.setToken(token);
        link.setSubjectRef(userId);
        link.setEmail(email);
        link.setLinkType(ActionLinkType.ACTIVATION);
        link.setExpiresAt(now.plusHours(24));
        link.setUsed(false);
        link.setOtpVerified(false);
        link.setMetadataJson("{\"tempPassword\":\"" + escapeJson(tempPassword) + "\"}");
        link.setCreatedAt(now);
        linkRepository.save(link);

        String url = normalizeBaseUrl(defaultFrontBaseUrl) + "/activate-account/" + token;
        return new ActivationGenerated(email, tempPassword, url);
    }

    @Transactional(readOnly = true)
    public LinkInfo getLinkByToken(String token) {
        NotificationActionLink link = linkRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));
        return toLinkInfo(link);
    }

    @Transactional
    public void verifyDemandeOtp(String linkToken, String otpInput) {
        String otp = normaliserOtp(otpInput);
        NotificationActionLink link = linkRepository.findByToken(linkToken)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();
        validateLinkActive(link, now);

        NotificationOtp otpEntity = otpRepository
                .findTopByLinkTokenAndContextAndUsedFalseOrderByCreatedAtDesc(linkToken, OtpContext.DEMANDE_OTP)
                .orElseThrow(() -> new IllegalStateException("Aucun OTP actif pour ce lien"));

        verifyOtpEntity(otpEntity, otp, now);
        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);

        link.setOtpVerified(true);
        linkRepository.save(link);
    }

    @Transactional
    public void verifyLoginOtp(String email, String otpInput) {
        String otp = normaliserOtp(otpInput);
        NotificationOtp otpEntity = otpRepository
                .findTopByEmailAndContextAndUsedFalseOrderByCreatedAtDesc(email.trim(), OtpContext.LOGIN_OTP)
                .orElseThrow(() -> new IllegalArgumentException("OTP invalide ou expiré"));

        verifyOtpEntity(otpEntity, otp, LocalDateTime.now());
        otpEntity.setUsed(true);
        otpRepository.save(otpEntity);
    }

    @Transactional
    public long validateConsentLink(String token) {
        NotificationActionLink link = linkRepository.findByTokenAndLinkType(token, ActionLinkType.CONSENTEMENT)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();
        if (link.isUsed()) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (link.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Lien expiré");
        }
        if (!link.isOtpVerified()) {
            throw new IllegalStateException("OTP non validé");
        }

        link.setUsed(true);
        link.setUsedAt(now);
        linkRepository.save(link);
        return link.getSubjectRef();
    }

    @Transactional(readOnly = true)
    public ActivationResolve resolveActivation(String token) {
        NotificationActionLink link = linkRepository.findByTokenAndLinkType(token, ActionLinkType.ACTIVATION)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));

        LocalDateTime now = LocalDateTime.now();
        if (link.isUsed()) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (link.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Token expiré");
        }
        return new ActivationResolve(link.getSubjectRef(), link.getEmail());
    }

    @Transactional
    public void consumeActivationLink(String token) {
        NotificationActionLink link = linkRepository.findByTokenAndLinkType(token, ActionLinkType.ACTIVATION)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide"));
        LocalDateTime now = LocalDateTime.now();
        link.setUsed(true);
        link.setUsedAt(now);
        linkRepository.save(link);
    }

    private void validateLinkActive(NotificationActionLink link, LocalDateTime now) {
        if (link.isUsed()) {
            throw new IllegalStateException("Lien déjà utilisé");
        }
        if (link.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("Lien expiré");
        }
    }

    private void verifyOtpEntity(NotificationOtp entity, String otp, LocalDateTime now) {
        if (entity.isUsed()) {
            throw new IllegalStateException("OTP déjà utilisé");
        }
        if (entity.getExpiresAt().isBefore(now)) {
            throw new IllegalStateException("OTP expiré");
        }
        if (entity.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new IllegalStateException("Trop de tentatives OTP");
        }
        if (!sha256(otp).equals(entity.getCodeHash())) {
            entity.setAttempts(entity.getAttempts() + 1);
            otpRepository.save(entity);
            throw new IllegalArgumentException("OTP invalide");
        }
    }

    private static String normaliserOtp(String otpInput) {
        if (otpInput == null || otpInput.isBlank()) {
            throw new IllegalArgumentException("Code OTP obligatoire");
        }
        String trimmed = otpInput.trim();
        if (!OTP_SIX.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Le code OTP doit contenir exactement 6 chiffres");
        }
        return trimmed;
    }

    private LinkInfo toLinkInfo(NotificationActionLink link) {
        return new LinkInfo(
                link.getToken(),
                link.getSubjectRef(),
                link.getEmail(),
                link.getLinkType().name(),
                link.getReferenceLabel(),
                link.getExpiresAt(),
                link.isUsed(),
                link.isOtpVerified()
        );
    }

    private String normalizeBaseUrl(String frontBaseUrl) {
        String base = StringUtils.hasText(frontBaseUrl) ? frontBaseUrl : defaultFrontBaseUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String generateOtp6() {
        return String.valueOf(RANDOM.nextInt(900_000) + 100_000);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur hash OTP", e);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record ConsentGenerated(String consentementUrl, String referenceDemande, String token) {}
    public record OtpGenerated(String otp, String email, String referenceDemande) {}
    public record LoginOtpGenerated(String email, String otp) {}
    public record ActivationGenerated(String email, String tempPassword, String activationUrl) {}
    public record LinkInfo(
            String token,
            Long subjectRef,
            String email,
            String linkType,
            String referenceLabel,
            LocalDateTime expiresAt,
            boolean used,
            boolean otpVerified) {}
    public record ActivationResolve(Long userId, String email) {}
}
