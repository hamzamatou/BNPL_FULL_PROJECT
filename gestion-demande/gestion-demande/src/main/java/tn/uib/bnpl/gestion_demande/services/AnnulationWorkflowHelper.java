package tn.uib.bnpl.gestion_demande.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * Effet métier d'annulation commerçant (API directe ou worker Camunda {@code annuler-demande}).
 */
@Service
public class AnnulationWorkflowHelper {

    public static final Set<String> STATUTS_ANNULABLES = Set.of(
            "CREE",
            "EN_ATTENTE_CONSENTEMENT",
            "EN_COURS_PRESCORING",
            "SOUMISE"
    );

    private final DemandeFinancementRepository demandeRepo;
    private final DemandeHistoriqueService historiqueService;

    public AnnulationWorkflowHelper(
            DemandeFinancementRepository demandeRepo,
            DemandeHistoriqueService historiqueService) {
        this.demandeRepo = demandeRepo;
        this.historiqueService = historiqueService;
    }

    public void verifierAnnulable(DemandeFinancement demande) {
        String statut = upper(demande.getStatut());
        if ("ANNULEE".equals(statut)) {
            return;
        }
        if (!STATUTS_ANNULABLES.contains(statut)) {
            throw new IllegalStateException(
                    "Annulation interdite pour le statut actuel : " + demande.getStatut());
        }
    }

    @Transactional
    public DemandeFinancement appliquerAnnulation(
            Long demandeId,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole) {
        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));

        String statut = upper(demande.getStatut());
        if ("ANNULEE".equals(statut)) {
            return demande;
        }
        verifierAnnulable(demande);

        String avant = demande.getStatut();
        LocalDateTime now = LocalDateTime.now();
        demande.setStatut("ANNULEE");
        demande.setDateDerniereMiseAJour(now);
        DemandeFinancement saved = demandeRepo.save(demande);

        Long acteurId = acteurUserId != null ? acteurUserId : demande.getCommercantUserId();
        String role = acteurRole != null && !acteurRole.isBlank() ? acteurRole : "COMMERCANT";

        historiqueService.enregistrer(
                saved.getId(),
                "ANNULATION",
                "Demande annulée",
                "Annulation par le commerçant",
                avant,
                "ANNULEE",
                acteurId,
                acteurEmail,
                role,
                now
        );
        return saved;
    }

    private static String upper(String statut) {
        return statut == null ? "" : statut.toUpperCase(Locale.ROOT);
    }
}
