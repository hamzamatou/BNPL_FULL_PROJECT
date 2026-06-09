package tn.uib.bnpl.gestion_demande.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ArchivageDemandeRequest(
        Long demandeId,
        String statutFinal,
        String referenceDemande,
        Long clientId,
        String cinClient,
        BigDecimal montant,
        Integer dureeMois,
        String typeProduit,
        String snapshotJson,
        String documentsMetadataJson,
        Long archiveParUserId,
        LocalDateTime dateCloture
) {}
