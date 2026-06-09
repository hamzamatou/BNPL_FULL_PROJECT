package tn.uib.bnpl.gestion_demande.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collections;
import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, InternalApiKeyFilter internalApiKeyFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .addFilterBefore(internalApiKeyFilter, BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/demandes/consentement/confirm",
                    "/api/actions-client/send-otp",
                    "/api/actions-client/verify-otp",
                    "/api/internal/**",
                    "/actuator/health"
                ).permitAll()
                .requestMatchers(
                    "/api/demandes/coherence",
                    "/api/demandes/recommandations",
                    "/api/demandes/analyse",
                    "/api/demandes/analyse-ia",
                    "/api/demandes/creation-complete",
                    "/api/demandes/par-client",
                    "/api/demandes/*/detail",
                    "/api/demandes/dossiers/dernier",
                    "/api/demandes/workflow/**"
                ).hasAuthority("COMMERCANT")
                .requestMatchers(
                    "/api/demandes/admin/**"
                ).hasAuthority("ADMIN")
                .requestMatchers(
                    "/api/prises-en-charge/**"
                ).hasAnyAuthority("BANQUE", "ANALYSTE_BANCAIRE")
                .requestMatchers(
                    "/api/demandes/documents/presigned"
                ).hasAnyAuthority("COMMERCANT", "BANQUE", "ANALYSTE_BANCAIRE")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    /**
     * Front Angular (dev) sur le port 4200 — requêtes cross-origin vers ce service (8081).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:4200",
                "http://127.0.0.1:4200"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "X-Internal-Api-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = extractRoleClaim(jwt);
            if (role == null || role.isBlank()) {
                return Collections.emptyList();
            }
            if (role.startsWith("ROLE_")) {
                role = role.substring("ROLE_".length());
            }
            return List.of(new SimpleGrantedAuthority(role));
        });
        return converter;
    }

    private static String extractRoleClaim(org.springframework.security.oauth2.jwt.Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role != null && !role.isBlank()) {
            return role.trim();
        }
        Object raw = jwt.getClaim("role");
        if (raw == null) {
            return null;
        }
        return raw.toString().trim();
    }
}
