package tn.uib.bnpl.gestion_demande.camunda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.DossierClient;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.PrescoringResultDto;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.PrescoringScoreRepository;
import tn.uib.bnpl.gestion_demande.services.DemandeHistoriqueService;
import tn.uib.bnpl.gestion_demande.services.HistoriqueIaDetails;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

@Component
public class PrescoringWorkflowHelper {

    private static final Logger log = LoggerFactory.getLogger(PrescoringWorkflowHelper.class);

    private final DemandeFinancementRepository demandeRepo;
    private final PrescoringScoreRepository prescoringScoreRepo;
    private final ScoringFeignClient scoringClient;
    private final ObjectMapper objectMapper;
    private final DemandeHistoriqueService historiqueService;

    public PrescoringWorkflowHelper(DemandeFinancementRepository demandeRepo,
                                    PrescoringScoreRepository prescoringScoreRepo,
                                    ScoringFeignClient scoringClient,
                                    ObjectMapper objectMapper,
                                    DemandeHistoriqueService historiqueService) {
        this.demandeRepo = demandeRepo;
        this.prescoringScoreRepo = prescoringScoreRepo;
        this.scoringClient = scoringClient;
        this.objectMapper = objectMapper;
        this.historiqueService = historiqueService;
    }

    @Transactional
    public PrescoringResultDto executerPrescoring(Long demandeId) {
        DemandeFinancement demande = demandeRepo.findByIdForWorkflow(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));
        DossierClient d = demande.getDossierClient();

        String statutAvantPrescoring = demande.getStatut();
        demande.setStatut("EN_COURS_PRESCORING");
        demande.setDateDerniereMiseAJour(LocalDateTime.now());
        demandeRepo.save(demande);
        historiqueService.enregistrer(
                demandeId,
                "PRESCORING",
                "Calcul prescoring en cours",
                "Appel du service ML prescoring",
                statutAvantPrescoring,
                "EN_COURS_PRESCORING",
                null,
                null,
                "SYSTEME",
                LocalDateTime.now()
        );

        try {
            PrescoringResultDto dto = scoringClient.prescore(
                    str(d.getRevenuMensuelNet()),
                    str(d.getRevenuAnnuel()),
                    str(d.getChargesMensuelles()),
                    str(demande.getMontant()),
                    str(demande.getDureeMois()),
                    str(d.getAncienneteEmploiMois()),
                    d.getTypeContrat()
            );
            String explicationsJson = objectMapper.writeValueAsString(
                    dto.explications() != null ? dto.explications() : List.of());
            String zoneCode = dto.zone() != null ? dto.zone().code() : "inconnu";
            PrescoringScore score = PrescoringScore.of(
                    demande, dto.pdPct(), dto.score(), zoneCode, explicationsJson);
            prescoringScoreRepo.save(score);
            demande.setPrescoringScore(score);
            demandeRepo.save(demande);
            historiqueService.enregistrer(
                    demandeId,
                    "PRESCORING",
                    "Prescoring réalisé",
                    "Score " + dto.score() + " — zone " + zoneCode,
                    "EN_COURS_PRESCORING",
                    "EN_COURS_PRESCORING",
                    null,
                    null,
                    "SYSTEME",
                    LocalDateTime.now(),
                    HistoriqueIaDetails.prescoring(
                            dto.score(),
                            dto.pdPct(),
                            zoneCode,
                            dto.explications())
            );
            log.info("Prescoring OK — demande={} score={}", demandeId, dto.score());
            return dto;
        } catch (Exception ex) {
            log.error("Prescoring échoué — demande={} : {}", demandeId, ex.getMessage());
            throw new RuntimeException("Prescoring indisponible", ex);
        }
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }
}
