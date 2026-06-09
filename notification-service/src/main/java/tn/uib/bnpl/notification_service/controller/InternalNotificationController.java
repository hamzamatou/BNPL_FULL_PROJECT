package tn.uib.bnpl.notification_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.notification_service.dto.OtpVerifyRequest;
import tn.uib.bnpl.notification_service.dto.TokenRequest;
import tn.uib.bnpl.notification_service.service.NotificationCredentialService;

import java.util.Map;

@RestController
@RequestMapping("/api/internal")
public class InternalNotificationController {

    private final NotificationCredentialService credentialService;

    public InternalNotificationController(NotificationCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<Map<String, Boolean>> verifyOtp(@RequestBody OtpVerifyRequest request) {
        if (request.otp() == null || request.otp().isBlank()) {
            throw new IllegalArgumentException("otp obligatoire");
        }
        String context = request.context() == null ? "" : request.context().trim().toUpperCase();
        if ("LOGIN_OTP".equals(context)) {
            if (request.email() == null || request.email().isBlank()) {
                throw new IllegalArgumentException("email obligatoire pour LOGIN_OTP");
            }
            credentialService.verifyLoginOtp(request.email(), request.otp());
        } else if ("DEMANDE_OTP".equals(context)) {
            if (request.linkToken() == null || request.linkToken().isBlank()) {
                throw new IllegalArgumentException("linkToken obligatoire pour DEMANDE_OTP");
            }
            credentialService.verifyDemandeOtp(request.linkToken(), request.otp());
        } else {
            throw new IllegalArgumentException("context inconnu: " + request.context());
        }
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @PostMapping("/links/by-token")
    public ResponseEntity<NotificationCredentialService.LinkInfo> getLink(@RequestBody TokenRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token obligatoire");
        }
        return ResponseEntity.ok(credentialService.getLinkByToken(request.token()));
    }

    @PostMapping("/links/validate-consent")
    public ResponseEntity<Map<String, Long>> validateConsent(@RequestBody TokenRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token obligatoire");
        }
        long demandeId = credentialService.validateConsentLink(request.token());
        return ResponseEntity.ok(Map.of("demandeId", demandeId));
    }

    @PostMapping("/activation/resolve")
    public ResponseEntity<NotificationCredentialService.ActivationResolve> resolveActivation(
            @RequestBody TokenRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token obligatoire");
        }
        return ResponseEntity.ok(credentialService.resolveActivation(request.token()));
    }

    @PostMapping("/activation/consume")
    public ResponseEntity<Map<String, Boolean>> consumeActivation(@RequestBody TokenRequest request) {
        if (request.token() == null || request.token().isBlank()) {
            throw new IllegalArgumentException("token obligatoire");
        }
        credentialService.consumeActivationLink(request.token());
        return ResponseEntity.ok(Map.of("consumed", true));
    }
}
