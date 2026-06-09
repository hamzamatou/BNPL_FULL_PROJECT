package tn.uib.bnpl.gestion_demande.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tn.uib.bnpl.gestion_demande.dto.DossierRoutageRequest;
import tn.uib.bnpl.gestion_demande.dto.RoutageEvaluerResponse;

@FeignClient(
        name = "scoring-banques",
        url = "${scoring.banques.service.url:http://localhost:8092}",
        configuration = FeignClientConfig.class
)
public interface BanqueScoringFeignClient {

    @PostMapping("/routage/evaluer")
    RoutageEvaluerResponse evaluerRoutage(@RequestBody DossierRoutageRequest dossier);
}
