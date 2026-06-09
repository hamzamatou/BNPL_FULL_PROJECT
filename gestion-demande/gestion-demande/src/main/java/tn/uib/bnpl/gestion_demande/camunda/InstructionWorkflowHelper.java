package tn.uib.bnpl.gestion_demande.camunda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;
import tn.uib.bnpl.gestion_demande.services.DemandeService;

/**
 * Effets métier des étapes Camunda du cycle instruction (compléments, décisions, refus partiel).
 */
@Component
public class InstructionWorkflowHelper {

    private static final Logger log = LoggerFactory.getLogger(InstructionWorkflowHelper.class);

    private final DemandeFinancementRepository demandeRepo;
    private final PriseEnChargeRepository priseEnChargeRepository;
    private final DemandeService demandeService;

    public InstructionWorkflowHelper(
            DemandeFinancementRepository demandeRepo,
            PriseEnChargeRepository priseEnChargeRepository,
            DemandeService demandeService) {
        this.demandeRepo = demandeRepo;
        this.priseEnChargeRepository = priseEnChargeRepository;
        this.demandeService = demandeService;
    }

    /**
     * Worker {@code demander-complement} : retour vers {@code Task_PrendreDecision}
     * (notification client déjà envoyée côté métier).
     */
    @Transactional(readOnly = true)
    public void traiterDemandeComplement(Long demandeId, String commentaire) {
        log.info("Compléments demandés (Camunda) — demande={}", demandeId);
    }

    /**
     * Worker {@code cloturer-archiver-demande} : archivage miroir puis purge BDD active.
     */
    @Transactional
    public void cloturerEtArchiverDemande(Long demandeId, String motifRefus, Long archiveParUserId) {
        DemandeFinancement demande = loadDemande(demandeId);
        String statutCloture = demande.getStatut();
        if (!"ACCEPTEE".equalsIgnoreCase(statutCloture) && !"REFUSEE".equalsIgnoreCase(statutCloture)) {
            throw new IllegalStateException(
                    "Clôture impossible — statut=" + statutCloture + " demande=" + demandeId);
        }
        statutCloture = statutCloture.toUpperCase();
        demandeService.archiverEtSupprimer(demandeId, statutCloture, archiveParUserId);
        log.info("Demande clôturée et archivée (Camunda) — id={} statut={}", demandeId, statutCloture);
    }

    /**
     * Worker {@code refus-partiel} : la BDD est déjà à jour ; vérifie qu'une autre banque peut reprendre.
     */
    @Transactional(readOnly = true)
    public void preparerRefusPartiel(Long demandeId) {
        DemandeFinancement demande = loadDemande(demandeId);
        long remainingRoute = priseEnChargeRepository.countRouteBanksNotRefused(demandeId);
        log.info("Refus partiel (Camunda) — demande={} statut={} banquesRouteRestantes={}",
                demandeId, demande.getStatut(), remainingRoute);
        if (remainingRoute == 0) {
            log.warn("Refus partiel sans banque ROUTE restante — demande={}", demandeId);
        }
    }

    private DemandeFinancement loadDemande(Long demandeId) {
        return demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));
    }
}
