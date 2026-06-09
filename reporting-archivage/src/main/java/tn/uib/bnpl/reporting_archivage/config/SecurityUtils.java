package tn.uib.bnpl.reporting_archivage.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        Object id = jwt.getClaim("id");
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id != null) {
            return Long.parseLong(id.toString());
        }
        throw new IllegalStateException("Claim JWT 'id' introuvable");
    }

    public static String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        String role = jwt.getClaimAsString("role");
        return role != null ? role.trim().toUpperCase() : null;
    }

    public static boolean isAnalysteBanque() {
        return "ANALYSTE_BANCAIRE".equals(getCurrentRole()) || "BANQUE".equals(getCurrentRole());
    }
}
