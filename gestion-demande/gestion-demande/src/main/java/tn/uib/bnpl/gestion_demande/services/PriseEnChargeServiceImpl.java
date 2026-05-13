package tn.uib.bnpl.gestion_demande.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;
import tn.uib.bnpl.gestion_demande.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PriseEnChargeServiceImpl implements PriseEnChargeService {

    private final PriseEnChargeRepository priseEnChargeRepository;
    private final DemandeFinancementRepository demandeRepo;

    public PriseEnChargeServiceImpl(
            PriseEnChargeRepository priseEnChargeRepository,
            DemandeFinancementRepository demandeRepo) {
        this.priseEnChargeRepository = priseEnChargeRepository;
        this.demandeRepo = demandeRepo;
    }

    @Override
    public List<DemandeFinancement> listerDemandesDisponiblesPourBanque() {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        return priseEnChargeRepository.findDemandesBanqueNonVerrouillees(banqueUserId, now);
    }

    @Override
    public List<DemandeFinancement> listerDemandesVerrouilleesPourBanque() {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        return priseEnChargeRepository.findDemandesAffecteesVerrouilleesPourBanque(
                banqueUserId, LocalDateTime.now());
    }

    @Override
    public DemandeFinancement getDemandeDetailPourBanqueVerrouillee(Long demandeId) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        priseEnChargeRepository.findActiveVerrouillage(demandeId, banqueUserId, now)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));

        return demandeRepo.findCompleteById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));
    }

    @Override
    public PriseEnCharge seSaisirEtDemarrerAnalyse(Long demandeId) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        if (!"SOUMISE".equalsIgnoreCase(demande.getStatut())) {
            throw new IllegalStateException("Demande non SOUMISE (statut=" + demande.getStatut() + ")");
        }

        long activeLocks = priseEnChargeRepository.countActiveVerrouillages(demandeId, now);
        if (activeLocks > 0) {
            throw new IllegalStateException("Demande déjà verrouillée (non expirée)");
        }

        PriseEnCharge pec = priseEnChargeRepository
                .findByDemandeIdAndBanqueUserIdAndStatut(demandeId, banqueUserId, "ROUTE")
                .orElseThrow(() -> new IllegalStateException(
                        "Prise en charge en ROUTE introuvable pour cette banque"));

        LocalDateTime expiration = now.plusHours(48);

        pec.setStatut("VERROUILLEE");
        pec.setDateVerrouillage(now);
        pec.setDateExpiration(expiration);
        pec.setDecision("EN_COURS");
        pec.setDateDebutTraitement(now);
        pec.setDateDecision(null);
        pec.setMotifRefus(null);
        pec.setCommentaire(null);

        PriseEnCharge saved = priseEnChargeRepository.save(pec);

        demande.setStatut("EN_COURS_ANALYSE");
        demande.setDateDerniereMiseAJour(now);
        demandeRepo.save(demande);

        return saved;
    }

    @Override
    public DemandeFinancement accepterDemande(Long demandeId, String commentaire) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        PriseEnCharge pec = priseEnChargeRepository
                .findActiveVerrouillage(demandeId, banqueUserId, now)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        pec.setDecision("ACCEPTEE");
        pec.setCommentaire(commentaire);
        pec.setDateDecision(now);

        pec.setStatut("DEVERROUILLEE");
        pec.setDateVerrouillage(null);
        pec.setDateExpiration(null);
        priseEnChargeRepository.save(pec);

        demande.setStatut("ACCEPTEE");
        demande.setDateDerniereMiseAJour(now);
        demandeRepo.save(demande);

        return demande;
    }

    @Override
    public DemandeFinancement refuserDemande(Long demandeId, String motifRefus, String commentaire) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        PriseEnCharge pec = priseEnChargeRepository
                .findActiveVerrouillage(demandeId, banqueUserId, now)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        pec.setDecision("REFUSEE");
        pec.setMotifRefus(motifRefus);
        pec.setCommentaire(commentaire);
        pec.setDateDecision(now);

        pec.setStatut("DEVERROUILLEE");
        pec.setDateVerrouillage(null);
        pec.setDateExpiration(null);
        priseEnChargeRepository.save(pec);

        long remainingRoute = priseEnChargeRepository.countRouteBanksNotRefused(demandeId);
        if (remainingRoute == 0) {
            demande.setStatut("REFUSEE");
        } else {
            demande.setStatut("SOUMISE");
        }
        demande.setDateDerniereMiseAJour(now);
        demandeRepo.save(demande);

        return demande;
    }

    @Override
    public DemandeFinancement demanderComplements(Long demandeId, String commentaire) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();

        PriseEnCharge pec = priseEnChargeRepository
                .findActiveVerrouillage(demandeId, banqueUserId, now)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        pec.setDecision("EN_COURS");
        pec.setCommentaire(commentaire);
        pec.setDateDebutTraitement(now);
        pec.setDateDecision(null);
        priseEnChargeRepository.save(pec);

        demande.setStatut("EN_COURS_ANALYSE");
        demande.setDateDerniereMiseAJour(now);
        return demandeRepo.save(demande);
    }
}
