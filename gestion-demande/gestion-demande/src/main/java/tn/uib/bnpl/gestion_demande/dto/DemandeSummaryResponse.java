package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Réponse légère pour éviter la sérialisation du graphe JPA (lazy relations).
 */
public record DemandeSummaryResponse(
        Long id,
        String referenceDemande,
        BigDecimal montant,
        String statut,
        LocalDateTime dateCreation,
        LocalDateTime dateDerniereMiseAJour,
        String typeProduit,
        Long clientId,
        String clientNom,
        String clientPrenom
) {}
