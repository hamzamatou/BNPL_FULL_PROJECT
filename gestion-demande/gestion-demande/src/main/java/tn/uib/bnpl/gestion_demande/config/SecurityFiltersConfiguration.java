package tn.uib.bnpl.gestion_demande.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filtre inter-services enregistré uniquement dans la chaîne Spring Security
 * (pas en filtre Servlet global — évite le double passage et Access Denied).
 */
@Configuration
public class SecurityFiltersConfiguration {

    @Bean
    InternalApiKeyFilter internalApiKeyFilter(@Value("${internal.api.key}") String internalApiKey) {
        return new InternalApiKeyFilter(internalApiKey);
    }

    @Bean
    FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilterRegistration(
            InternalApiKeyFilter filter) {
        FilterRegistrationBean<InternalApiKeyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
