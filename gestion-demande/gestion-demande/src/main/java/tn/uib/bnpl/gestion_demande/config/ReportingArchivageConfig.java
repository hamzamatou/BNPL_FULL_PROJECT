package tn.uib.bnpl.gestion_demande.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ReportingArchivageConfig {

    @Bean
    RestClient reportingArchivageRestClient(
            @Value("${reporting-archivage.url:http://localhost:8083}") String baseUrl,
            @Value("${internal.api.key}") String internalApiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }
}
