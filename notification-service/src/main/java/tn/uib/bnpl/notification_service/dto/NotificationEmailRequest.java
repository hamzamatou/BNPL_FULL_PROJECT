package tn.uib.bnpl.notification_service.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationEmailRequest(
        String eventId,
        String correlationId,
        String internalApiKey,
        String to,
        String templateCode,
        Map<String, Object> data,
        LocalDateTime createdAt
) {}

