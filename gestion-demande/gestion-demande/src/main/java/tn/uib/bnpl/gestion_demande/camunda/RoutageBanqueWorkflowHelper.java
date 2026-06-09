package tn.uib.bnpl.gestion_demande.camunda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.DossierClient;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.config.BanqueScoringFeignClient;
import tn.uib.bnpl.gestion_demande.config.ClientUtilisateurFeign;
import tn.uib.bnpl.gestion_demande.dto.DossierRoutageRequest;
import tn.uib.bnpl.gestion_demande.dto.RoutageEvaluerResponse;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;
import tn.uib.bnpl.gestion_demande.services.DemandeHistoriqueService;
import tn.uib.bnpl.gestion_demande.services.NotificationPublisher;
import tn.uib.bnpl.gestion_demande.services.RoutageBanqueAnalysteResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Routage Camunda : évalue {@code banquesRoutees}, résout les analystes actifs par banque
 * via gestion-utilisateur, crée une {@link PriseEnCharge} ROUTE par analyste, puis SOUMISE.
 */
@Component
public class RoutageBanqueWorkflowHelper {

    private static final Logger log = LoggerFactory.getLogger(RoutageBanqueWorkflowHelper.class);

    private final DemandeFinancementRepository demandeRepo;
    private final PriseEnChargeRepository priseEnChargeRepo;
    private final BanqueScoringFeignClient banqueScoringClient;
    private final RoutageBanqueAnalysteResolver analysteResolver;
    private final DemandeHistoriqueService historiqueService;
    private final NotificationPublisher notificationPublisher;

    public RoutageBanqueWorkflowHelper(DemandeFinancementRepository demandeRepo,
                                       PriseEnChargeRepository priseEnChargeRepo,
                                       BanqueScoringFeignClient banqueScoringClient,
                                       RoutageBanqueAnalysteResolver analysteResolver,
                                       DemandeHistoriqueService historiqueService,
                                       NotificationPublisher notificationPublisher) {
        this.demandeRepo = demandeRepo;
        this.priseEnChargeRepo = priseEnChargeRepo;
        this.banqueScoringClient = banqueScoringClient;
        this.analysteResolver = analysteResolver;
        this.historiqueService = historiqueService;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public void routerDemande(Long demandeId) {
        DemandeFinancement demande = demandeRepo.findByIdForWorkflow(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));

        String statutAvant = demande.getStatut();
        DossierClient dossier = demande.getDossierClient();
        if (dossier == null) {
            throw new IllegalStateException("Dossier client manquant pour la demande " + demandeId);
        }

        RoutageEvaluerResponse evaluation = banqueScoringClient.evaluerRoutage(toDossierRoutage(dossier, demande));
        List<String> banquesRoutees = evaluation != null && evaluation.banquesRoutees() != null
                ? evaluation.banquesRoutees()
                : Collections.emptyList();

        if (banquesRoutees.isEmpty()) {
            log.warn("Routage sans banquesRoutees — demande={}", demandeId);
        }

        int scorePrescoring = demande.getPrescoringScore() != null
                ? demande.getPrescoringScore().getScore()
                : 50;

        int pecCreees = 0;
        for (String codeBanque : banquesRoutees) {
            List<ClientUtilisateurFeign.AnalysteRoutageResponse> analystes =
                    analysteResolver.resolveAnalystesActifs(codeBanque);
            if (analystes.isEmpty()) {
                log.warn("Aucun analyste actif pour codeBanque={} — demande={} (aligner Banque.codeBanque en base)",
                        codeBanque, demandeId);
                continue;
            }
            String nomBanque = analystes.stream()
                    .map(ClientUtilisateurFeign.AnalysteRoutageResponse::getNomBanque)
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElse(codeBanque);
            for (ClientUtilisateurFeign.AnalysteRoutageResponse analyste : analystes) {
                Long banqueUserId = analyste.getId();
                if (banqueUserId == null) {
                    continue;
                }
                boolean created = priseEnChargeRepo.findByDemandeIdAndBanqueUserId(demandeId, banqueUserId)
                        .map(pec -> {
                            log.debug("Prise en charge déjà existante — demande={} analyste={} codeBanque={}",
                                    demandeId, banqueUserId, codeBanque);
                            return false;
                        })
                        .orElseGet(() -> {
                            PriseEnCharge pec = new PriseEnCharge(demande, banqueUserId, scorePrescoring, "ROUTE");
                            priseEnChargeRepo.save(pec);
                            log.info("Routage ROUTE créé — demande={} codeBanque={} analysteUserId={}",
                                    demandeId, codeBanque, banqueUserId);
                            return true;
                        });
                if (created) {
                    pecCreees++;
                    notificationPublisher.publishNouvelleDemandeAnalyste(
                            demande,
                            analyste.getEmail(),
                            analyste.getNom(),
                            analyste.getPrenom(),
                            nomBanque,
                            codeBanque);
                }
            }
        }

        demande.setStatut("SOUMISE");
        demande.setDateDerniereMiseAJour(LocalDateTime.now());
        demandeRepo.save(demande);

        String libelleBanques = banquesRoutees.stream()
                .map(c -> c == null ? "?" : c.toUpperCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        historiqueService.enregistrer(
                demandeId,
                "ROUTAGE",
                "Routage vers les banques",
                pecCreees + " prise(s) en charge ROUTE — banques : " + libelleBanques,
                statutAvant,
                "SOUMISE",
                null,
                null,
                "SYSTEME",
                LocalDateTime.now()
        );

        log.info("Routage terminé — demande={} banquesRoutees={} pecCreees={}",
                demandeId, libelleBanques, pecCreees);
    }

    private static DossierRoutageRequest toDossierRoutage(DossierClient dossier, DemandeFinancement demande) {
        return new DossierRoutageRequest(
                toDouble(dossier.getRevenuMensuelNet()),
                toDouble(dossier.getChargesMensuelles()),
                toDouble(demande.getMontant()),
                demande.getDureeMois() != null ? demande.getDureeMois() : 0,
                dossier.getAncienneteEmploiMois() != null ? dossier.getAncienneteEmploiMois() : 0,
                dossier.getTypeContrat() != null ? dossier.getTypeContrat() : "CDI",
                0,
                50.0
        );
    }

    private static double toDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
