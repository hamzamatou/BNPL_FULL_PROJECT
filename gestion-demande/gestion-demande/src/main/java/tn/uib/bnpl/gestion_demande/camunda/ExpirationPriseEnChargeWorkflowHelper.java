package tn.uib.bnpl.gestion_demande.camunda;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.StatutDemande;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;
import tn.uib.bnpl.gestion_demande.services.DemandeHistoriqueService;

import java.time.LocalDateTime;

/**
 * Expiration de la fenêtre 48 h (prise en charge sans démarrage d'analyse).
 */
@Component
public class ExpirationPriseEnChargeWorkflowHelper {

    private static final Logger log = LoggerFactory.getLogger(ExpirationPriseEnChargeWorkflowHelper.class);

    private final PriseEnChargeRepository priseEnChargeRepository;
    private final DemandeFinancementRepository demandeRepo;
    private final DemandeHistoriqueService historiqueService;

    public ExpirationPriseEnChargeWorkflowHelper(
            PriseEnChargeRepository priseEnChargeRepository,
            DemandeFinancementRepository demandeRepo,
            DemandeHistoriqueService historiqueService) {
        this.priseEnChargeRepository = priseEnChargeRepository;
        this.demandeRepo = demandeRepo;
        this.historiqueService = historiqueService;
    }

    @Transactional
    public void expirerFenetre48h(Long demandeId) {
        LocalDateTime now = LocalDateTime.now();
        PriseEnCharge pec = priseEnChargeRepository.findFenetre48hExpiree(demandeId, now).orElse(null);
        if (pec == null) {
            log.info("Aucune prise en charge à expirer — demande={}", demandeId);
            return;
        }

        DemandeFinancement demande = pec.getDemande();
        Long banqueUserId = pec.getBanqueUserId();

        pec.setStatut("EXPIRE");
        pec.setDateVerrouillage(null);
        pec.setDateExpiration(null);
        priseEnChargeRepository.save(pec);

        String avant = demande.getStatut();
        if (!StatutDemande.ACCEPTEE.equalsIgnoreCase(avant) && !StatutDemande.REFUSEE.equalsIgnoreCase(avant)
                && !StatutDemande.isRejetAutoPrescoring(avant)) {
            demande.setStatut(StatutDemande.SOUMISE);
            demande.setDateDerniereMiseAJour(now);
            demandeRepo.save(demande);
        }

        historiqueService.enregistrer(
                demande.getId(),
                "EXPIRATION",
                "Fenêtre 48 h expirée",
                "Aucune décision — demande disponible pour une autre banque",
                avant,
                StatutDemande.SOUMISE,
                banqueUserId,
                null,
                "BANQUE",
                now
        );
        log.info("Prise en charge expirée — demande={} banque={}", demandeId, banqueUserId);
    }
}
