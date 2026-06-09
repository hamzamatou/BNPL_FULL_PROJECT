package tn.uib.bnpl.notification_service.service;

import org.springframework.stereotype.Service;

@Service
public class UtilisateurNotificationGenerator {

    private final NotificationCredentialService credentialService;

    public UtilisateurNotificationGenerator(NotificationCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    public NotificationCredentialService.ActivationGenerated generateActivation(Long userId, String email) {
        return credentialService.createActivationLink(userId, email);
    }

    public NotificationCredentialService.LoginOtpGenerated generateLoginOtp(String email) {
        return credentialService.createLoginOtp(email);
    }
}
