package tn.uib.bnpl.gestion_demande.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Identifiant utilisateur = claim {@code id} du JWT (aligné avec gestion-utilisateur).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException("JWT attendu, reçu: "
                    + (auth == null ? "null" : auth.getClass().getName()));
        }
        Jwt jwt = jwtAuth.getToken();
        Object idClaim = jwt.getClaim("id");
        if (idClaim == null) {
            idClaim = jwt.getClaim("userId");
        }
        if (idClaim instanceof Number n) {
            return n.longValue();
        }
        if (idClaim instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Claim 'id' invalide dans le JWT");
            }
        }
        throw new IllegalStateException("JWT sans claim 'id' (sub est l'email, ne pas l'utiliser comme id)");
    }

    public static String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object r = jwtAuth.getToken().getClaim("role");
            return r != null ? r.toString() : null;
        }
        return null;
    }
}
