package tn.uib.bnpl.gestion_demande.web;

import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.classes.*;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.services.ClientRemoteService;
import tn.uib.bnpl.gestion_demande.services.DemandeHistoriqueService;

import java.util.List;

@Component
public class DemandeDtoMapper {

    private final ClientRemoteService clientRemoteService;
    private final DemandeHistoriqueService historiqueService;

    public DemandeDtoMapper(ClientRemoteService clientRemoteService,
                            DemandeHistoriqueService historiqueService) {
        this.clientRemoteService = clientRemoteService;
        this.historiqueService = historiqueService;
    }

    public DemandeSummaryResponse toSummary(DemandeFinancement d) {
        DossierClient dos = d.getDossierClient();
        ClientIdentityDto client = fetchClientIdentity(dos);
        return new DemandeSummaryResponse(
                d.getId(),
                d.getReferenceDemande(),
                d.getMontant(),
                d.getDureeMois(),
                d.getStatut(),
                d.getDateCreation(),
                d.getDateDerniereMiseAJour(),
                d.getTypeProduit(),
                dos != null ? dos.getClientId() : null,
                client != null ? client.nom() : null,
                client != null ? client.prenom() : null,
                client != null ? client.cin() : null,
                d.getCommercantUserId()
        );
    }

    public DemandeCompleteResponse toComplete(DemandeFinancement d) {
        DossierClient dossier = d.getDossierClient();
        ClientIdentityDto client = fetchClientIdentity(dossier);
        return new DemandeCompleteResponse(
                d.getId(),
                d.getReferenceDemande(),
                d.getMontant(),
                d.getDureeMois(),
                d.getStatut(),
                d.getDateCreation(),
                d.getDateDerniereMiseAJour(),
                d.getTypeProduit(),
                client != null ? new DemandeCompleteResponse.ClientLiteDto(
                        client.id(),
                        client.nom(),
                        client.prenom(),
                        client.cin(),
                        client.telephone(),
                        client.email()
                ) : null,
                mapDossier(dossier),
                mapRecommandation(d.getRecommandation()),
                mapPrescoring(d.getPrescoringScore()),
                historiqueService.listerPourDemande(d)
        );
    }

    private DemandeCompleteResponse.DossierClientDto mapDossier(DossierClient dos) {
        if (dos == null) return null;
        return new DemandeCompleteResponse.DossierClientDto(
                dos.getId(),
                dos.getReferenceDossier(),
                dos.getDateCreation(),
                dos.getAncienneteEmploiMois(),
                dos.getTypeContrat(),
                dos.getRevenuMensuelNet(),
                dos.getAutresRevenusMensuels(),
                dos.getLoyerMensuel(),
                dos.getMensualitesCredits(),
                dos.getAutresChargesFixes(),
                dos.getChargesMensuelles(),
                dos.getEncoursCredits(),
                dos.getTauxEndettement(),
                dos.getSituationFamiliale(),
                dos.getNombreEnfants(),
                mapDocuments(dos.getDocuments())
        );
    }

    private List<DemandeCompleteResponse.DocumentDossierDto> mapDocuments(List<DocumentDossier> docs) {
        if (docs == null) return List.of();
        return docs.stream().map(doc -> new DemandeCompleteResponse.DocumentDossierDto(
                doc.getId(),
                doc.getTypeDocument(),
                doc.getObjectKey(),
                doc.getNomFichier(),
                doc.getContentType(),
                doc.getTailleOctets()
        )).toList();
    }

    private DemandeCompleteResponse.RecommandationRDto mapRecommandation(Recommandation r) {
        if (r == null) return null;
        return new DemandeCompleteResponse.RecommandationRDto(
                r.getId(),
                r.getRecommandationsJson(),
                r.getGeneratedAt()
        );
    }

    /**
     * PrescoringScore — champs exacts de l'entité :
     *   getId()                → Long
     *   getProbabiliteDefaut() → double
     *   getScore()             → int
     *   getZoneCode()          → String   ("vert" | "orange" | "rouge")
     *   getExplicationsJson()  → String
     *   getComputedAt()        → LocalDateTime
     *
     * ABSENT de l'entité : getZoneLibelle() → supprimé du mapper et du DTO.
     */
    private DemandeCompleteResponse.PrescoringScoreRDto mapPrescoring(PrescoringScore ps) {
        if (ps == null) return null;
        return new DemandeCompleteResponse.PrescoringScoreRDto(
                ps.getId(),
                ps.getProbabiliteDefaut(),
                ps.getScore(),
                ps.getZoneCode(),
                ps.getExplicationsJson(),
                ps.getComputedAt()
        );
    }

    private ClientIdentityDto fetchClientIdentity(DossierClient dossier) {
        if (dossier == null || dossier.getClientId() == null) return null;
        try {
            return clientRemoteService.getClientIdentity(dossier.getClientId());
        } catch (Exception ignored) {
            return null;
        }
    }
}