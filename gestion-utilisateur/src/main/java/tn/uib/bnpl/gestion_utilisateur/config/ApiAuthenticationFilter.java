package tn.uib.bnpl.gestion_utilisateur.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ApiAuthenticationFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(ApiAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final String internalApiKey;

    public ApiAuthenticationFilter(JwtUtil jwtUtil, String internalApiKey) {
        this.jwtUtil = jwtUtil;
        this.internalApiKey = internalApiKey;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = normalizeApiPath(request);

        // 🚫 skip authentication for public routes
        if (path.startsWith("/api/users/login")
                || path.startsWith("/api/users/register")
                || path.startsWith("/api/users/verify-otp")
                || path.startsWith("/api/users/activate")) {

            filterChain.doFilter(request, response);
            return;
        }

        // Inter-services (gestion-demande Feign, reporting, etc.) : @PreAuthorize("INTERNAL")
        if (path.startsWith("/api/internal/")) {
            applyInternalApiKeyAuth(request, response, filterChain);
            return;
        }

        applyJwtAuth(request);
        filterChain.doFilter(request, response);
    }

    private void applyInternalApiKeyAuth(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws IOException, ServletException {
        String provided = request.getHeader("X-Internal-Api-Key");

        if (internalApiKey == null || internalApiKey.isBlank() || !internalApiKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid internal API key");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                List.of(new SimpleGrantedAuthority("INTERNAL"))
        );

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private void applyJwtAuth(HttpServletRequest request) {

        String token = extractBearerToken(request.getHeader("Authorization"));

        if (token == null || token.isEmpty() || !jwtUtil.validateToken(token)) {
            return;
        }

        try {
            Claims body = jwtUtil.parseClaims(token);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();

            String role = extractRoleClaim(body);

            if (role != null && !role.isBlank()) {
                authorities.add(new SimpleGrantedAuthority(role));
            }

            if (authorities.isEmpty()) {
                log.warn("⚠ Aucun rôle trouvé dans le token !");
            }

            // ✅ CORRECTION ICI
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            body.getSubject(),
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception ex) {
            log.warn("Erreur JWT: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private static String extractBearerToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) return null;

        if (!authHeader.startsWith("Bearer ")) return null;

        return authHeader.substring(7);
    }

    private static String extractRoleClaim(Claims claims) {
        Object raw = claims.get("role");
        if (raw == null) return null;

        String role = raw.toString().trim();
        return role.isEmpty() ? null : role;
    }

    private static String normalizeApiPath(HttpServletRequest request) {
        String p = request.getServletPath();

        if (p == null || p.isBlank()) {
            p = request.getRequestURI();
        }

        while (p.contains("//")) {
            p = p.replace("//", "/");
        }

        if (!p.startsWith("/")) {
            p = "/" + p;
        }

        return p;
    }
}