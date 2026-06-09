package tn.uib.bnpl.gestion_demande.services;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.DernierDossierFinancierResponse;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;

import java.util.List;

public interface DemandeService {


    DemandeFinancement validerConsentementEtSoumettre(String token);

    List<DemandeFinancement> listerDemandesParClient(Long clientId);
    List<DemandeSummaryResponse> listerDemandesParCommercant(Long commercantId);

    /** Toutes les demandes présentes dans la table demande_financement (supervision admin). */
    List<DemandeSummaryResponse> listerDemandesEnCoursPourAdmin();

    DemandeFinancement getDemandeParIdPourCommercant(Long demandeId);
    DemandeFinancement annulerDemande(Long demandeId);
    DemandeFinancement renvoyerMailConsentement(Long demandeId);

    DernierDossierFinancierResponse getDernierDossierFinancierParCin(String cin);

    String getPresignedDocumentUrl(String objectKey);

	DemandeFinancement creerDemandeComplete(
            CreationDemandeCompleteRequest request,
            String recommandationsJson,
            String processInstanceId);

    /** Archivage miroir puis purge de la demande active (Camunda clôture). */
    void archiverEtSupprimer(Long demandeId, String statutFinal, Long archiveParUserId);
}
