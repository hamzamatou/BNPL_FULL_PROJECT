package tn.uib.bnpl.gestion_demande.web;

import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.DocumentDossier;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.dto.DemandeCompleteResponse;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;
import tn.uib.bnpl.gestion_demande.dto.DocumentDossierResponse;
import tn.uib.bnpl.gestion_demande.dto.DossierClientResponse;
import tn.uib.bnpl.gestion_demande.services.ClientRemoteService;

import java.util.List;

@Component
public class DemandeDtoMapper {

    private final ClientRemoteService clientRemoteService;

    public DemandeDtoMapper(ClientRemoteService clientRemoteService) {
        this.clientRemoteService = clientRemoteService;
    }

    public DemandeSummaryResponse toSummary(DemandeFinancement d) {
        Long clientId = d.getDossierClient() != null ? d.getDossierClient().getClientId() : null;
        String nom = null;
        String prenom = null;
        if (clientId != null) {
            try {
                ClientIdentityDto identity = clientRemoteService.getClientIdentity(clientId);
                nom = identity.nom();
                prenom = identity.prenom();
            } catch (Exception ignored) {
            }
        }
        return new DemandeSummaryResponse(
                d.getId(),
                d.getReferenceDemande(),
                d.getMontant(),
                d.getStatut(),
                d.getDateCreation(),
                d.getDateDerniereMiseAJour(),
                d.getTypeProduit(),
                clientId,
                nom,
                prenom
        );
    }

    public DemandeCompleteResponse toComplete(DemandeFinancement d) {
        Long clientId = d.getDossierClient() != null ? d.getDossierClient().getClientId() : null;
        ClientIdentityDto client = null;
        if (clientId != null) {
            try {
                client = clientRemoteService.getClientIdentity(clientId);
            } catch (Exception ignored) {
            }
        }

        DossierClientResponse dossier = null;
        if (d.getDossierClient() != null) {
            var dc = d.getDossierClient();
            List<DocumentDossierResponse> documents = dc.getDocuments() == null
                    ? List.of()
                    : dc.getDocuments().stream().map(this::toDocument).toList();

            dossier = new DossierClientResponse(
                    dc.getId(),
                    dc.getClientId(),
                    dc.getReferenceDossier(),
                    dc.getDateCreation(),
                    dc.getDateDerniereMiseAJour(),
                    dc.getSituationFamiliale(),
                    dc.getNombreEnfants(),
                    dc.getAncienneteEmploiMois(),
                    dc.getTypeContrat(),
                    dc.getRevenuMensuelNet(),
                    dc.getAutresRevenusMensuels(),
                    dc.getRevenuAnnuel(),
                    dc.getLoyerMensuel(),
                    dc.getMensualitesCredits(),
                    dc.getAutresChargesFixes(),
                    dc.getChargesMensuelles(),
                    dc.getEncoursCredits(),
                    dc.getTauxEndettement(),
                    documents
            );
        }

        return new DemandeCompleteResponse(
                d.getId(),
                d.getReferenceDemande(),
                d.getMontant(),
                d.getDureeMois(),
                d.getStatut(),
                d.getDateCreation(),
                d.getDateDerniereMiseAJour(),
                d.getTypeProduit(),
                client,
                dossier
        );
    }

    private DocumentDossierResponse toDocument(DocumentDossier doc) {
        return new DocumentDossierResponse(
                doc.getId(),
                doc.getTypeDocument(),
                doc.getObjectKey(),
                doc.getNomFichier(),
                doc.getContentType(),
                doc.getTailleOctets()
        );
    }
}
