package tn.uib.bnpl.gestion_demande.web;

import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.classes.*;
import tn.uib.bnpl.gestion_demande.dto.*;

import java.util.List;

@Component
public class DemandeDtoMapper {

    public DemandeSummaryResponse toSummary(DemandeFinancement d) {
        DossierClient dos = d.getDossierClient();
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
                null,
                null,
                null
        );
    }

    public DemandeCompleteResponse toComplete(DemandeFinancement d) {
        return new DemandeCompleteResponse(
                d.getId(),
                d.getReferenceDemande(),
                d.getMontant(),
                d.getDureeMois(),
                d.getStatut(),
                d.getDateCreation(),
                d.getDateDerniereMiseAJour(),
                d.getTypeProduit(),
                null,
                mapDossier(d.getDossierClient()),
                mapRecommandation(d.getRecommandation()),
                mapPrescoring(d.getPrescoringScore())
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
}