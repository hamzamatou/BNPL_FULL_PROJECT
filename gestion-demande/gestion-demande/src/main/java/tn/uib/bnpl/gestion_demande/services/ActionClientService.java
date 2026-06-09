package tn.uib.bnpl.gestion_demande.services;



import tn.uib.bnpl.gestion_demande.classes.TypeActionClient;



public interface ActionClientService {



    void requestConsentementEmail(Long demandeId, String emailClient, TypeActionClient typeAction, String frontBaseUrl);



    void sendOtp(String token, String nom, String prenom, String cin);



    void verifyOtp(String token, String otpInput);



    Long validateTokenForConsent(String token);

}

