package tn.uib.bnpl.notification_service.service;

import org.springframework.stereotype.Service;

@Service
public class DemandeNotificationGenerator {

    private final NotificationCredentialService credentialService;

    public DemandeNotificationGenerator(NotificationCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    public NotificationCredentialService.ConsentGenerated generateConsentLink(
            Long demandeId,
            String emailClient,
            String frontBaseUrl,
            String referenceDemande) {
        return credentialService.createConsentLink(demandeId, emailClient, frontBaseUrl, referenceDemande);
    }

    public NotificationCredentialService.OtpGenerated generateDemandeOtp(String linkToken) {
        return credentialService.createDemandeOtp(linkToken);
    }
}
