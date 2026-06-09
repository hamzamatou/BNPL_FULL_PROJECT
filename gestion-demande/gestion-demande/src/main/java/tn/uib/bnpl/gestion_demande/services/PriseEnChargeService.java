package tn.uib.bnpl.gestion_demande.services;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;

import java.util.List;

/**
 * Seul point d'entrée métier pour la prise en charge banque (ex-Traitement + listes / détail banque).
 */
public interface PriseEnChargeService {

    List<DemandeFinancement> listerDemandesDisponiblesPourBanque();

    /** Demandes routées (ROUTE) pour l'analyste connecté — DTO prêt pour l'API. */
    List<DemandeSummaryResponse> listerDemandesDisponiblesResumePourBanque();

    List<DemandeFinancement> listerDemandesVerrouilleesPourBanque();

    List<DemandeSummaryResponse> listerDemandesVerrouilleesResumePourBanque();

    DemandeFinancement getDemandeDetailPourBanqueVerrouillee(Long demandeId);

    /** Récap complet avant prise en charge ou pour une demande déjà verrouillée par l'analyste. */
    DemandeFinancement getRecapDemandePourBanque(Long demandeId);

    /** Prise en charge : {@code SOUMISE} → {@code EN_COURS_ANALYSE} (diagramme d'états). */
    PriseEnCharge seSaisir(Long demandeId);

    /**
     * Optionnel : marque l'instruction comme démarrée côté métier (annule la fenêtre 48 h en base).
     * Sinon, le premier accepter / refuser / complément fait la même chose.
     */
    PriseEnCharge demarrerAnalyse(Long demandeId);

    /** @deprecated même effet que {@link #seSaisir(Long)} + {@link #demarrerAnalyse(Long)} */
    PriseEnCharge seSaisirEtDemarrerAnalyse(Long demandeId);

    DemandeFinancement accepterDemande(Long demandeId, String commentaire);

    DemandeFinancement refuserDemande(Long demandeId, String motifRefus, String commentaire);

    DemandeFinancement demanderComplements(Long demandeId, String commentaire);

    /** {@code EN_ATTENTE_COMPLEMENT} → {@code EN_COURS_ANALYSE} après envoi client. */
    DemandeFinancement receptionnerComplements(Long demandeId, String detail);
}
