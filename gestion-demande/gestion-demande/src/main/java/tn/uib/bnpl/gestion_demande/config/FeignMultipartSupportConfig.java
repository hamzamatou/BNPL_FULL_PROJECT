package tn.uib.bnpl.gestion_demande.config;

import feign.Request;
import feign.codec.Encoder;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import feign.form.spring.SpringFormEncoder;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignMultipartSupportConfig {

    /**
     * Délais HTTP pour {@link ScoringFeignClient} : les propriétés
     * {@code spring.cloud.openfeign.client.config.*} ne s'appliquent pas de façon fiable
     * quand un {@code configuration = …} dédié est utilisé sans fusion explicite.
     * Aligné sur {@code service-coherence-ocr/test_micro.mjs} ({@code timeoutSec} défaut 600).
     */
    @Bean
    @Scope("prototype")
    Request.Options scoringServiceHttpOptions() {
        return new Request.Options(20, TimeUnit.SECONDS, 600, TimeUnit.SECONDS, true);
    }

    @Bean
    Encoder feignFormEncoder(ObjectFactory<HttpMessageConverters> converters) {
        return new SpringFormEncoder(new SpringEncoder(converters));
    }
}