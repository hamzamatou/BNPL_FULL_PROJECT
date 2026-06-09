package tn.uib.bnpl.reporting_archivage.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Snapshot complet envoyé par gestion-demande avant purge locale.
 */
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
