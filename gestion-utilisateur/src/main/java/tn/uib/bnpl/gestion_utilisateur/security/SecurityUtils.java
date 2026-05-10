package tn.uib.bnpl.gestion_utilisateur.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Lit l’identifiant utilisateur depuis le JWT OAuth2 ({@link JwtAuthenticationToken}).
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return la valeur du claim {@code id} du JWT courant
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Utilisateur non authentifié");
        }
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(
                    "Authentification attendue: JwtAuthenticationToken, reçu: "
                            + (auth == null ? "null" : auth.getClass().getName()));
        }
        Jwt jwt = jwtAuth.getToken();
        Object id = jwt.getClaim("id");
        if (id == null) {
            throw new IllegalStateException("JWT sans claim 'id' valide");
        }
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id instanceof String s) {
            return Long.valueOf(s);
        }
        throw new IllegalStateException("Claim 'id' invalide dans le JWT");
    }

    /**
     * Rôle métier depuis le claim {@code role} (ex. COMMERCANT).
     */
    public static String getCurrentUserRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object role = jwtAuth.getToken().getClaim("role");
            return role != null ? role.toString() : null;
        }
        return null;
    }
}
