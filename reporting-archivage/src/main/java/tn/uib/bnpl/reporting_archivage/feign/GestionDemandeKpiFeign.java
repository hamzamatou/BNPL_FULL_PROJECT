package tn.uib.bnpl.reporting_archivage.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import tn.uib.bnpl.reporting_archivage.config.FeignClientConfig;
import tn.uib.bnpl.reporting_archivage.feign.dto.DemandesActivesKpiFeignDto;
import tn.uib.bnpl.reporting_archivage.feign.dto.DemandesDashboardKpiFeignDto;

import java.util.Map;

@FeignClient(
        name = "gestion-demande-kpi",
        url = "${gestion-demande.url}",
        configuration = FeignClientConfig.class
)
public interface GestionDemandeKpiFeign {

    @GetMapping("/api/internal/kpi/demandes-actives")
    DemandesActivesKpiFeignDto demandesActives();

    @GetMapping("/api/internal/kpi/repartition-statuts-actifs")
    Map<String, Long> repartitionStatutsActifs();

    @GetMapping("/api/internal/kpi/dashboard-demandes")
    DemandesDashboardKpiFeignDto dashboardDemandes();
}
