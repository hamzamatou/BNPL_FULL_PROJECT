package tn.uib.bnpl.gestion_utilisateur.services;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.dto.audit.AuditEventPayload;
import tn.uib.bnpl.gestion_utilisateur.dto.audit.AuditEventRequest;
import tn.uib.bnpl.gestion_utilisateur.client.ReportingArchivageClient;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AccesAuditService {

    private static final Logger log = LoggerFactory.getLogger(AccesAuditService.class);

    private final ReportingArchivageClient reportingArchivageClient;
    private final boolean enabled;

    public AccesAuditService(
            ReportingArchivageClient reportingArchivageClient,
            @Value("${app.audit.reporting.enabled:true}") boolean enabled) {
        this.reportingArchivageClient = reportingArchivageClient;
        this.enabled = enabled;
    }

    public void publierConnexion(User user, HttpServletRequest request) {
        publierAcces("CONNEXION", "Connexion réussie", user, request, "/api/users/verify-otp");
    }

    public void publierDeconnexion(Long userId, String email, Role role, HttpServletRequest request) {
        if (!enabled) {
            return;
        }
        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        user.setRole(role);
        publierAcces("DECONNEXION", "Déconnexion utilisateur", user, request, "/api/users/logout");
    }

    private void publierAcces(String typeAcces, String libelle, User user, HttpServletRequest request, String endpoint) {
        if (!enabled || user == null) {
            return;
        }
        try {
            String role = user.getRole() != null ? user.getRole().name() : null;
            String description = libelle + " — " + user.getEmail() + (role != null ? " (" + role + ")" : "");

            AuditEventPayload payload = new AuditEventPayload(
                    null, null,
                    typeAcces,
                    description,
                    null,
                    null, null, null,
                    null, null, null,
                    null, null, null,
                    user.getId(),
                    user.getEmail(),
                    role,
                    clientIp(request),
                    request != null ? request.getHeader("User-Agent") : null,
                    endpoint,
                    request != null ? request.getMethod() : null,
                    false,
                    null, null, null, null, null, null, null, null, null
            );

            AuditEventRequest event = new AuditEventRequest(
                    "ACCES_PLATEFORME",
                    "acces-" + typeAcces.toLowerCase() + "-" + user.getId() + "-" + UUID.randomUUID(),
                    null,
                    LocalDateTime.now(),
                    payload
            );

            reportingArchivageClient.publierEvenementAudit(event);
            log.debug("Audit {} envoyé pour {}", typeAcces, user.getEmail());
        } catch (Exception ex) {
            log.warn("Impossible d'envoyer l'audit {} vers reporting-archivage: {}", typeAcces, ex.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
