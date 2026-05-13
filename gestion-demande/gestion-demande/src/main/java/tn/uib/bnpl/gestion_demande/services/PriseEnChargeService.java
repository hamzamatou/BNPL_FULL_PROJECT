package tn.uib.bnpl.gestion_demande.services;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;

import java.util.List;

/**
 * Seul point d'entrée métier pour la prise en charge banque (ex-Traitement + listes / détail banque).
 */
public interface PriseEnChargeService {

    List<DemandeFinancement> listerDemandesDisponiblesPourBanque();

    List<DemandeFinancement> listerDemandesVerrouilleesPourBanque();

    DemandeFinancement getDemandeDetailPourBanqueVerrouillee(Long demandeId);

    PriseEnCharge seSaisirEtDemarrerAnalyse(Long demandeId);

    DemandeFinancement accepterDemande(Long demandeId, String commentaire);

    DemandeFinancement refuserDemande(Long demandeId, String motifRefus, String commentaire);

    DemandeFinancement demanderComplements(Long demandeId, String commentaire);
}
