package tn.uib.bnpl.gestion_demande.config;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.dto.*;

@FeignClient(
        name = "scoring-service",
        url = "${scoring.service.url}",
        configuration = FeignMultipartSupportConfig.class
)
public interface ScoringFeignClient {

    /**
     * Cohérence + recommandations (uniquement si anomalies[] vide) — appel unique depuis gestion-demande.
     */
    @PostMapping(
            value = "/dossier/validate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    DossierValidationResultDto validateDossier(
            @RequestPart("declared_data") String declaredData,
            @RequestPart(value = "cin", required = false) MultipartFile cin,
            @RequestPart(value = "fiche_paie_m1", required = false) MultipartFile fichePaieM1,
            @RequestPart(value = "fiche_paie_m2", required = false) MultipartFile fichePaieM2,
            @RequestPart(value = "fiche_paie_m3", required = false) MultipartFile fichePaieM3,
            @RequestPart(value = "attestation_travail", required = false) MultipartFile attestationTravail,
            @RequestPart(value = "devis", required = false) MultipartFile devis,
            @RequestPart(value = "justificatif_loyer", required = false) MultipartFile justificatifLoyer
    );

    @PostMapping(
            value = "/coherence/check",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    CoherenceResultDto checkCoherence(

            @RequestPart("declared_data")
            String declaredData,

            @RequestPart("cin")
            MultipartFile cin,

            @RequestPart("fiche_paie_m1")
            MultipartFile fichePaieM1,

            @RequestPart("fiche_paie_m2")
            MultipartFile fichePaieM2,

            @RequestPart("fiche_paie_m3")
            MultipartFile fichePaieM3,

            @RequestPart("attestation_travail")
            MultipartFile attestationTravail
    );

    @GetMapping("/recommendation/generate")
    RecommandationResultDto generateRecommandation(
            @RequestParam("revenu_mensuel_net") String revenuMensuelNet,
            @RequestParam("charges_mensuelles_totales") String chargesMensuellesTotales,
            @RequestParam("mensualites_credits_existants") String mensualitesCreditsExistants,
            @RequestParam("encours_credits") String encoursCredits,
            @RequestParam("anciennete_emploi_mois") String ancienneteEmploiMois,
            @RequestParam("montant_financement") String montantFinancement,
            @RequestParam("duree_mois") String dureeMois
    );

    @GetMapping("/prescoring/prescore")
    PrescoringResultDto prescore(
            @RequestParam("revenu_mensuel_net") String revenuMensuelNet,
            @RequestParam("revenu_annuel") String revenuAnnuel,
            @RequestParam("charges_mensuelles_totales") String chargesMensuellesTotales,
            @RequestParam("montant_demande") String montantDemande,
            @RequestParam("nbr_mois_remboursement") String nbrMoisRemboursement,
            @RequestParam("anciennete_emploi_mois") String ancienneteEmploiMois,
            @RequestParam("type_contrat") String typeContrat
    );
}