package tn.uib.bnpl.gestion_demande.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
public class FeignClientConfig {

    @Bean
    public RequestInterceptor feignJwtInterceptor(
            @Value("${internal.api.key}") String internalApiKey) {
        return requestTemplate -> {
            // Toujours fournir la clé inter-services pour les routes /api/internal/** sur 8080.
            requestTemplate.header("X-Internal-Api-Key", internalApiKey);

            // On conserve aussi le Bearer si présent (pas de régression JWT côté parcours commerçant).
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                String token = jwtAuth.getToken().getTokenValue();
                requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
        };
    }
}