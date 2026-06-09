package tn.uib.bnpl.gestion_demande.camunda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;
import tn.uib.bnpl.gestion_demande.classes.StatutDemande;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.services.ClientRemoteService;
import tn.uib.bnpl.gestion_demande.services.DemandeHistoriqueService;
import tn.uib.bnpl.gestion_demande.services.HistoriqueIaDetails;
import tn.uib.bnpl.gestion_demande.services.NotificationPublisher;

import java.time.LocalDateTime;

/**
 * Rejet automatique prescoring (PD &gt; 60 %) : statut {@code REJETEE_AUTO} + e-mail client via RabbitMQ.
 */
@Component
public class RejetAutoPrescoringWorkflowHelper {

    private static final Logger log = LoggerFactory.getLogger(RejetAutoPrescoringWorkflowHelper.class);

    private final DemandeFinancementRepository demandeRepo;
    private final DemandeHistoriqueService historiqueService;
    private final ClientRemoteService clientRemoteService;
    private final NotificationPublisher notificationPublisher;
    private final CamundaWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public RejetAutoPrescoringWorkflowHelper(DemandeFinancementRepository demandeRepo,
                                             DemandeHistoriqueService historiqueService,
                                             ClientRemoteService clientRemoteService,
                                             NotificationPublisher notificationPublisher,
                                             CamundaWorkflowService workflowService,
                                             ObjectMapper objectMapper) {
        this.demandeRepo = demandeRepo;
        this.historiqueService = historiqueService;
        this.clientRemoteService = clientRemoteService;
        this.notificationPublisher = notificationPublisher;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void traiterRejetAuto(Long demandeId) {
        DemandeFinancement demande = demandeRepo.findByIdForWorkflow(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));

        PrescoringScore score = demande.getPrescoringScore();
        if (score == null) {
            log.warn("Rejet auto sans prescoring_score — demande={}", demandeId);
        } else if (!workflowService.isRejetAutoPrescoring(score)) {
            log.warn("Rejet auto Camunda alors que PD <= 60 % — demande={} pd={}%",
                    demandeId, score.getProbabiliteDefaut());
        }

        String statutAvant = demande.getStatut();
        demande.setStatut(StatutDemande.REJETEE_AUTO);
        demande.setDateDerniereMiseAJour(LocalDateTime.now());
        demandeRepo.save(demande);

        historiqueService.enregistrer(
                demandeId,
                "REJET_AUTO",
                "Rejet automatique prescoring",
                score != null
                        ? "PD " + score.getProbabiliteDefaut() + " % — score " + score.getScore()
                        : "PD > 60 % — demande non routée vers les banques",
                statutAvant,
                StatutDemande.REJETEE_AUTO,
                null,
                null,
                "SYSTEME",
                LocalDateTime.now(),
                HistoriqueIaDetails.prescoringFromEntity(score, objectMapper)
        );

        Long clientId = demande.getDossierClient() != null ? demande.getDossierClient().getClientId() : null;
        ClientIdentityDto client = clientId != null ? clientRemoteService.getClientIdentity(clientId) : null;
        notificationPublisher.publishRejetAutoPrescoring(demande, score, client);

        log.info("Rejet auto prescoring traite — demande={} statut={}", demandeId, StatutDemande.REJETEE_AUTO);
    }
}
