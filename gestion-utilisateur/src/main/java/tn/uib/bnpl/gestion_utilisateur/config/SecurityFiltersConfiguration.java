package tn.uib.bnpl.gestion_utilisateur.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Déclare ApiAuthenticationFilter comme bean Spring Security UNIQUEMENT.
 * Le FilterRegistrationBean désactive l'enregistrement automatique Servlet
 * pour éviter le double enregistrement (racine du problème 403).
 */
@Configuration
public class SecurityFiltersConfiguration {

    @Bean
    public ApiAuthenticationFilter apiAuthenticationFilter(
            JwtUtil jwtUtil,
            @Value("${internal.api.key}") String internalApiKey) {
        return new ApiAuthenticationFilter(jwtUtil, internalApiKey);
    }

    @Bean
    public FilterRegistrationBean<ApiAuthenticationFilter> apiAuthenticationFilterRegistration(
            ApiAuthenticationFilter filter) {
        FilterRegistrationBean<ApiAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // ✅ empêche le double enregistrement Servlet
        return registration;
    }
}