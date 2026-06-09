package tn.uib.bnpl.gestion_demande.services;



import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;

import tn.uib.bnpl.gestion_demande.classes.TypeActionClient;

import tn.uib.bnpl.gestion_demande.client.NotificationServiceClient;

import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;

import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;



import java.util.Locale;

import java.util.regex.Pattern;

import java.time.LocalDateTime;



@Service

@Transactional

public class ActionClientServiceImpl implements ActionClientService {



    private static final Logger log = LoggerFactory.getLogger(ActionClientServiceImpl.class);

    private static final Pattern OTP_SIX_CHIFFRES = Pattern.compile("^\\d{6}$");



    private final DemandeFinancementRepository demandeRepo;

    private final ClientRemoteService clientRemoteService;

    private final NotificationPublisher notificationPublisher;

    private final NotificationServiceClient notificationClient;

    private final DemandeHistoriqueService historiqueService;



    public ActionClientServiceImpl(

            DemandeFinancementRepository demandeRepo,

            ClientRemoteService clientRemoteService,

            NotificationPublisher notificationPublisher,

            NotificationServiceClient notificationClient,
            DemandeHistoriqueService historiqueService) {

        this.demandeRepo = demandeRepo;

        this.clientRemoteService = clientRemoteService;

        this.notificationPublisher = notificationPublisher;

        this.notificationClient = notificationClient;

        this.historiqueService = historiqueService;

    }



    @Override

    public void requestConsentementEmail(Long demandeId, String emailClient, TypeActionClient typeAction, String frontBaseUrl) {

        publishConsentNotification(demandeId, emailClient, frontBaseUrl, typeAction.name());

    }



    @Override

    public void sendOtp(String token, String nom, String prenom, String cin) {

        NotificationServiceClient.LinkInfo link = notificationClient.getLinkByToken(token);

        if (link.used()) {

            throw new IllegalStateException("Lien déjà utilisé");

        }

        validateClientIdentity(link.subjectRef(), nom, prenom, cin);

        publishOtpNotification(link.subjectRef(), link.email(), token, nom, prenom, cin);

    }



    @Override

    public void verifyOtp(String token, String otpInput) {

        requireTokenNonVide(token);

        normaliserEtValiderOtpSaisi(otpInput);

        notificationClient.verifyDemandeOtp(token, otpInput.trim());

    }



    @Override

    public Long validateTokenForConsent(String token) {

        return notificationClient.validateConsentLink(token);

    }



    private void validateClientIdentity(Long demandeId, String nom, String prenom, String cin) {

        DemandeFinancement demande = demandeRepo.findById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable"));



        Long clientId = demande.getDossierClient().getClientId();

        ClientIdentityDto identity = clientRemoteService.getClientIdentity(clientId);



        boolean match =

                normalize(identity.nom()).equals(normalize(nom)) &&

                normalize(identity.prenom()).equals(normalize(prenom)) &&

                normalize(identity.cin()).equals(normalize(cin));



        if (!match) {

            throw new IllegalArgumentException("Identité client invalide");

        }

    }



    private static void requireTokenNonVide(String token) {

        if (token == null || token.isBlank()) {

            throw new IllegalArgumentException("Token obligatoire");

        }

    }



    private static String normaliserEtValiderOtpSaisi(String otpInput) {

        if (otpInput == null) {

            throw new IllegalArgumentException("Code OTP obligatoire");

        }

        String trimmed = otpInput.trim();

        if (trimmed.isEmpty()) {

            throw new IllegalArgumentException("Code OTP obligatoire");

        }

        if (!OTP_SIX_CHIFFRES.matcher(trimmed).matches()) {

            throw new IllegalArgumentException("Le code OTP doit contenir exactement 6 chiffres");

        }

        return trimmed;

    }



    private static String normalize(String s) {

        if (s == null) {

            return "";

        }

        return s.trim().toLowerCase(Locale.ROOT);

    }



    private void publishConsentNotification(Long demandeId, String emailClient, String frontBaseUrl, String typeAction) {
        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        try {
            notificationPublisher.publishConsentementLink(demande, emailClient, frontBaseUrl, typeAction);
            passerEnAttenteConsentement(demandeId);
        } catch (Exception ex) {
            log.error("Publication RabbitMQ consentement echouee (demandeId={})", demandeId, ex);
            throw new IllegalStateException("Impossible de publier la notification consentement", ex);
        }
    }



    private void publishOtpNotification(
            Long demandeId,
            String emailClient,
            String token,
            String nom,
            String prenom,
            String cin) {

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        try {
            notificationPublisher.publishOtpCode(demande, emailClient, token, nom, prenom, cin);
        } catch (Exception ex) {
            log.error("Publication RabbitMQ OTP echouee (demandeId={})", demandeId, ex);
            throw new IllegalStateException("Impossible de publier la notification OTP", ex);
        }
    }



    private void passerEnAttenteConsentement(Long demandeId) {

        demandeRepo.findById(demandeId).ifPresent(demande -> {

            if (!"CREE".equalsIgnoreCase(demande.getStatut())) {

                return;

            }

            LocalDateTime now = LocalDateTime.now();

            String avant = demande.getStatut();

            demande.setStatut("EN_ATTENTE_CONSENTEMENT");

            demande.setDateDerniereMiseAJour(now);

            demandeRepo.save(demande);

            historiqueService.enregistrer(
                    demande.getId(),
                    "CONSENTEMENT_ENVOYE",
                    "Consentement client demandé",
                    "E-mail de validation envoyé au client",
                    avant,
                    "EN_ATTENTE_CONSENTEMENT",
                    demande.getCommercantUserId(),
                    null,
                    "COMMERCANT",
                    now
            );

            log.info("Demande {} — statut EN_ATTENTE_CONSENTEMENT", demande.getReferenceDemande());

        });

    }

}

