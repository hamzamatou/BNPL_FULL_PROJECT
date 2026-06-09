package tn.uib.bnpl.gestion_utilisateur.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class NotificationServiceClient {

    private final RestClient restClient;
    private final String internalApiKey;

    public NotificationServiceClient(
            @Value("${notification-service.url:http://localhost:8082}") String baseUrl,
            @Value("${internal.api.key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public void verifyLoginOtp(String email, String otp) {
        post("/api/internal/otp/verify", Map.of(
                "context", "LOGIN_OTP",
                "email", email,
                "otp", otp
        ));
    }

    public ActivationResolve resolveActivation(String token) {
        return post("/api/internal/activation/resolve", Map.of("token", token), ActivationResolve.class);
    }

    public void consumeActivation(String token) {
        post("/api/internal/activation/consume", Map.of("token", token));
    }

    private void post(String uri, Object body) {
        post(uri, body, Void.class);
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
            String msg = ex.getResponseBodyAsString();
            if (msg == null || msg.isBlank()) {
                msg = ex.getMessage();
            }
            throw new IllegalArgumentException(msg, ex);
        }
    }

    public record ActivationResolve(Long userId, String email) {}
}
