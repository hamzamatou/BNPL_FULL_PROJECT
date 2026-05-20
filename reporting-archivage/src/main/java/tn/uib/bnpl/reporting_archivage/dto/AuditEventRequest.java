package tn.uib.bnpl.reporting_archivage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Événement générique reçu via REST interne ou RabbitMQ (BPM / microservices).
 */
public record AuditEventRequest(
        @NotBlank String eventType,
        String correlationId,
        String internalApiKey,
        LocalDateTime occurredAt,
        @NotNull AuditEventPayload payload
) {}
