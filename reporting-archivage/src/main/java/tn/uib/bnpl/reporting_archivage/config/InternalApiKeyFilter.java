package tn.uib.bnpl.reporting_archivage.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie les appels inter-services (BPM, gestion-demande) via X-Internal-Api-Key.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String internalApiKey;

    public InternalApiKeyFilter(@Value("${internal.api.key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith(request.getContextPath() + "/api/internal")) {
            String key = request.getHeader("X-Internal-Api-Key");
            if (key != null && key.equals(internalApiKey)) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "internal-service",
                        null,
                        List.of(new SimpleGrantedAuthority("INTERNAL"))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }

}
