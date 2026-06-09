package tn.uib.bnpl.gestion_demande.dto;
 
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
 
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeSummaryResponse(
        Long          id,
        String        referenceDemande,
        BigDecimal    montant,
        Integer       dureeMois,
        String        statut,
        LocalDateTime dateCreation,
        LocalDateTime dateDerniereMiseAJour,
        String        typeProduit,
        Long          clientId,
        String        clientNom,
        String        clientPrenom,
        String        clientCin,
        Long          commercantUserId
) {}
 