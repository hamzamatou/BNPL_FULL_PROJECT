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

        if (isPublicUserRoute(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = normalizeApiPath(request);

        // Inter-services (gestion-demande Feign, reporting, etc.) : @PreAuthorize("INTERNAL")
        if (path.startsWith("/api/internal/") || (request.getRequestURI() != null && request.getRequestURI().contains("/api/internal/"))) {
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

    private static boolean isPublicUserRoute(HttpServletRequest request) {
        String path = normalizeApiPath(request);
        String uri = request.getRequestURI() != null ? request.getRequestURI() : "";
        return matchesPublicUserPath(path) || matchesPublicUserPath(uri);
    }

    private static boolean matchesPublicUserPath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.contains("/api/users/login")
                || value.contains("/api/users/register")
                || value.contains("/api/users/verify-otp")
                || value.contains("/api/users/resend-otp")
                || value.contains("/api/users/activate");
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