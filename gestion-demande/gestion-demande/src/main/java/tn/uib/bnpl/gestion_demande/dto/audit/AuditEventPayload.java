package tn.uib.bnpl.gestion_demande.dto.audit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload aligné sur reporting-archivage ({@code AuditEventPayload}).
 */
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
) {
    public static AuditEventPayload actionDemande(
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
            LocalDateTime occurredAt
    ) {
        return new AuditEventPayload(
                demandeId,
                referenceDemande,
                type,
                libelle,
                detailsJson,
                acteurUserId,
                acteurEmail,
                acteurRole,
                statutAvant,
                statutApres,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }

    public static AuditEventPayload decisionFinancement(
            Long demandeId,
            String referenceDemande,
            String type,
            String libelle,
            String detailsJson,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole,
            LocalDateTime occurredAt
    ) {
        return new AuditEventPayload(
                demandeId,
                referenceDemande,
                type,
                libelle,
                detailsJson,
                acteurUserId,
                acteurEmail,
                acteurRole,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                occurredAt
        );
    }
}
