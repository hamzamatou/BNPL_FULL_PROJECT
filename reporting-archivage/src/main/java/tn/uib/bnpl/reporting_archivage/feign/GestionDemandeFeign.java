package tn.uib.bnpl.reporting_archivage.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.uib.bnpl.reporting_archivage.config.FeignClientConfig;

import java.util.Map;

@FeignClient(
        name = "gestion-demande",
        url = "${gestion-demande.url}",
        configuration = FeignClientConfig.class
)
public interface GestionDemandeFeign {

    @GetMapping("/api/demandes/{id}/detail")
    Map<String, Object> getDemandeDetail(@PathVariable("id") Long id);
}
