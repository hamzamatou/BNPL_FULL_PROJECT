package tn.uib.bnpl.gestion_demande.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Valide X-Internal-Api-Key pour /api/internal/** (même principe que gestion-utilisateur).
 * Pas de rôle INTERNAL requis sur les contrôleurs — la clé suffit.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String internalApiKey;

    public InternalApiKeyFilter(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isInternalPath(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader("X-Internal-Api-Key");
        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid internal API key");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isInternalPath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path != null && !path.isBlank() && path.startsWith("/api/internal/")) {
            return true;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        String context = request.getContextPath();
        String prefix = (context != null ? context : "") + "/api/internal/";
        return uri.startsWith(prefix);
    }
}
