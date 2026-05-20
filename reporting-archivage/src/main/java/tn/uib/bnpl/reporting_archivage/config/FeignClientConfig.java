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
            requestTemplate.header("X-Internal-Api-Key", internalApiKey);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                requestTemplate.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAuth.getToken().getTokenValue());
            }
        };
    }
}
