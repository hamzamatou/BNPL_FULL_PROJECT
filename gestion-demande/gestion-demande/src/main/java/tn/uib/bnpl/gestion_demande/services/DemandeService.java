package tn.uib.bnpl.gestion_demande.services;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.DernierDossierFinancierResponse;

import java.util.List;

public interface DemandeService {

    DemandeFinancement creerDemandeComplete(CreationDemandeCompleteRequest request);

    DemandeFinancement validerConsentementEtSoumettre(String token);

    List<DemandeFinancement> listerDemandesParClient(Long clientId);

    DemandeFinancement getDemandeParIdPourCommercant(Long demandeId);

    DernierDossierFinancierResponse getDernierDossierFinancierParCin(String cin);

    String getPresignedDocumentUrl(String objectKey);
}
