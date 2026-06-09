package tn.uib.bnpl.gestion_utilisateur.dto;

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
