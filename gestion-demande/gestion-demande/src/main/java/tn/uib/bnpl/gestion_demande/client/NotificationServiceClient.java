package tn.uib.bnpl.gestion_demande.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class NotificationServiceClient {

    private final RestClient restClient;
    private final String internalApiKey;
    private final ObjectMapper objectMapper;

    public NotificationServiceClient(
            @Value("${notification-service.url:http://localhost:8082}") String baseUrl,
            @Value("${internal.api.key}") String internalApiKey,
            ObjectMapper objectMapper) {
        this.internalApiKey = internalApiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public LinkInfo getLinkByToken(String token) {
        return post("/api/internal/links/by-token", Map.of("token", token), LinkInfo.class);
    }

    public void verifyDemandeOtp(String linkToken, String otp) {
        post("/api/internal/otp/verify", Map.of(
                "context", "DEMANDE_OTP",
                "linkToken", linkToken,
                "otp", otp
        ), Void.class);
    }

    public long validateConsentLink(String token) {
        Map<?, ?> body = post("/api/internal/links/validate-consent", Map.of("token", token), Map.class);
        Object demandeId = body.get("demandeId");
        if (demandeId instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(demandeId.toString());
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            var spec = restClient.post()
                    .uri(uri)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
            if (responseType == Void.class) {
                spec.retrieve().toBodilessEntity();
                return null;
            }
            return spec.retrieve().body(responseType);
        } catch (RestClientResponseException ex) {
            String msg = extractRemoteMessage(ex);
            if (ex.getStatusCode().value() == 409) {
                throw new IllegalStateException(msg, ex);
            }
            throw new IllegalArgumentException(msg, ex);
        } catch (ResourceAccessException ex) {
            throw new IllegalStateException(
                    "Service de notification indisponible. Veuillez réessayer.",
                    ex
            );
        }
    }

    private String extractRemoteMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                if (root.hasNonNull("message")) {
                    return root.get("message").asText();
                }
            } catch (Exception ignored) {
                // Corps non JSON : message brut si lisible
            }
            String trimmed = body.trim();
            if (!trimmed.startsWith("{")) {
                return trimmed;
            }
        }
        return switch (ex.getStatusCode().value()) {
            case 400 -> "OTP invalide";
            case 401, 403 -> "Accès refusé au service de notification.";
            case 404 -> "Service de notification introuvable.";
            case 409 -> "OTP expiré";
            default -> "Erreur technique lors de la vérification. Veuillez réessayer.";
        };
    }

    public record LinkInfo(
            String token,
            Long subjectRef,
            String email,
            String linkType,
            String referenceLabel,
            String expiresAt,
            boolean used,
            boolean otpVerified) {}
}
