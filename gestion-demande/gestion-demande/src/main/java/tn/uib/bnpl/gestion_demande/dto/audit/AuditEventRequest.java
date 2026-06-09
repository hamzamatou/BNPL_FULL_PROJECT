package tn.uib.bnpl.gestion_demande.dto.audit;

import java.time.LocalDateTime;

public record AuditEventRequest(
        String eventType,
        String correlationId,
        String internalApiKey,
        LocalDateTime occurredAt,
        AuditEventPayload payload
) {
}
