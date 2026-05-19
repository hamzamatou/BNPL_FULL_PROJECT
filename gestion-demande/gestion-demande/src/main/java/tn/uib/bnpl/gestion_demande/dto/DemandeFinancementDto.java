package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO retourné par POST /creation-complete (HTTP 201).
 * Produit par DemandeDtoMapper.toSummary(DemandeFinancement).
 *
 * Contient les champs essentiels pour qu'Angular puisse :
 *  - afficher la référence en step 6
 *  - stocker l'id pour un éventuel appel ultérieur à /{id}/detail
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DemandeFinancementDto(

        Long            id,
        String          referenceDemande,
        BigDecimal      montant,
        Integer         dureeMois,
        String          statut,
        LocalDateTime   dateCreation,
        LocalDateTime   dateDerniereMiseAJour,
        String          typeProduit,

        // Identité du client (renseignée si le mapper la charge)
        Long            clientId,
        String          clientNom,
        String          clientPrenom,
        String          clientCin
) {}