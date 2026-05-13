package tn.uib.bnpl.gestion_demande.services;

import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;

import java.time.LocalDate;

public interface ClientRemoteService {

    Long creerClient(String nom, String prenom, String email, String telephone, String cin,
                     String adresse, String sexe, String profession, String employeur,
                     LocalDate dateNaissance);

    void modifierClient(Long clientId, String nom, String prenom, String email, String telephone, String cin,
                        String adresse, String sexe, String profession, String employeur,
                        LocalDate dateNaissance);

    ClientIdentityDto getClientIdentity(Long clientId);

    /** Récupère l'id du client (rôle CLIENT) à partir du CIN. */
    Long getClientIdByCin(String cin);
}