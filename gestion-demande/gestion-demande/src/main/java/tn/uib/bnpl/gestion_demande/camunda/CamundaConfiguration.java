package tn.uib.bnpl.gestion_demande.camunda;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
/*// Condition de chargement : active toute la configuration uniquement si camunda.enabled=true*/
@ConditionalOnProperty(name = "camunda.enabled", havingValue = "true")

@EnableConfigurationProperties(CamundaProperties.class)
public class CamundaConfiguration {

    @Bean
    RestTemplate camundaRestTemplate() {
        return new RestTemplate();
    }
}
