package tn.uib.bnpl.reporting_archivage.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import tn.uib.bnpl.reporting_archivage.config.FeignClientConfig;
import tn.uib.bnpl.reporting_archivage.feign.dto.ReferentielKpiFeignDto;

import java.util.Map;

@FeignClient(
        name = "gestion-utilisateur-kpi",
        url = "${gestion-utilisateur.url}",
        configuration = FeignClientConfig.class
)
public interface GestionUtilisateurKpiFeign {

    @GetMapping("/api/internal/kpi/referentiel")
    ReferentielKpiFeignDto referentiel();

    @GetMapping("/api/internal/kpi/commercants-labels")
    Map<String, String> commercantsLabels();
}
