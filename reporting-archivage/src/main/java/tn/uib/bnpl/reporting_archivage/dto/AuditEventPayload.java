package tn.uib.bnpl.reporting_archivage.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AuditEventPayload(
        Long demandeId,
        String referenceDemande,
        String type,
        String libelle,
        String detailsJson,
        Long acteurUserId,
        String acteurEmail,
        String acteurRole,
        String statutAvant,
        String statutApres,
        String etapeWorkflow,
        Long documentId,
        String objectKey,
        String typeDocument,
        Long userId,
        String userEmail,
        String userRole,
        String adresseIp,
        String userAgent,
        String endpoint,
        String methodeHttp,
        Boolean suspect,
        String statutFinal,
        Long clientId,
        String cinClient,
        BigDecimal montant,
        Integer dureeMois,
        String typeProduit,
        String snapshotJson,
        String documentsMetadataJson,
        LocalDateTime dateCloture
) {}
