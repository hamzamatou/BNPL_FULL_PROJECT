package tn.uib.bnpl.reporting_archivage.config;

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
    RequestInterceptor feignJwtInterceptor(@Value("${internal.api.key}") String internalApiKey) {
        return requestTemplate -> {
            String path = requestTemplate.path();
            boolean internalCall = path != null && path.startsWith("/api/internal/");

            if (internalCall) {
                // KPI inter-services : clé INTERNAL uniquement (pas le JWT admin)
                requestTemplate.header("X-Internal-Api-Key", internalApiKey);
                requestTemplate.removeHeader(HttpHeaders.AUTHORIZATION);
                return;
            }

            requestTemplate.header("X-Internal-Api-Key", internalApiKey);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
            }
        };
    }
}
