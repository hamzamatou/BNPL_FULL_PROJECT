package tn.uib.bnpl.gestion_demande.dto;
 
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
 
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeCompleteResponse(
 
        Long          id,
        String        referenceDemande,
        BigDecimal    montant,
        Integer       dureeMois,
        String        statut,
        LocalDateTime dateCreation,
        LocalDateTime dateDerniereMiseAJour,
        String        typeProduit,
 
        ClientLiteDto       client,
        DossierClientDto    dossierClient,
        RecommandationRDto  recommandation,
        PrescoringScoreRDto prescoringScore
) {
    // ── Sous-DTOs inline ────────────────────────────────────────────────────
 
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientLiteDto(
            Long   id,
            String nom,
            String prenom,
            String cin,
            String telephone,
            String email
    ) {}
 
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DossierClientDto(
            Long          id,
            String        referenceDossier,
            LocalDateTime dateCreation,
            Integer       ancienneteEmploiMois,
            String        typeContrat,
            BigDecimal    revenuMensuelNet,
            BigDecimal    autresRevenusMensuels,
            BigDecimal    loyerMensuel,
            BigDecimal    mensualitesCredits,
            BigDecimal    autresChargesFixes,
            BigDecimal    chargesMensuelles,
            BigDecimal    encoursCredits,
            BigDecimal    tauxEndettement,
            String        situationFamiliale,
            Integer       nombreEnfants,
            List<DocumentDossierDto> documents
    ) {}
 
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DocumentDossierDto(
            Long   id,
            String typeDocument,
            String objectKey,
            String nomFichier,
            String contentType,
            Long   tailleOctets
    ) {}
 
    /** Recommandation persistée — recommandationsJson est un JSON string → string[] */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecommandationRDto(
            Long          id,
            String        recommandationsJson,
            LocalDateTime generatedAt
    ) {}
 
    /** Score prescoring persisté après consentement */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrescoringScoreRDto(
            Long          id,
            Double        probabiliteDefaut,
            Integer       score,
            String        zoneCode,
            String        explicationsJson,
            LocalDateTime computedAt
    ) {}
}