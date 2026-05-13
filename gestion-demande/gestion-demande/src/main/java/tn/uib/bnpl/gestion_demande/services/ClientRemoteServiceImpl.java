package tn.uib.bnpl.gestion_demande.services;


import feign.FeignException;
import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_demande.config.ClientUtilisateurFeign;
import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.services.ClientRemoteService;

import java.time.LocalDate;

@Service
public class ClientRemoteServiceImpl implements ClientRemoteService {

    private final ClientUtilisateurFeign clientUtilisateurFeign;

    public ClientRemoteServiceImpl(ClientUtilisateurFeign clientUtilisateurFeign) {
        this.clientUtilisateurFeign = clientUtilisateurFeign;
    }

    @Override
    public Long creerClient(String nom, String prenom, String email, String telephone, String cin,
                            String adresse, String sexe, String profession, String employeur,
                            LocalDate dateNaissance) {
        ClientUtilisateurFeign.ClientCreationRequest req = buildRequest(
                nom, prenom, email, telephone, cin, adresse, sexe, profession, employeur, dateNaissance
        );

        ClientUtilisateurFeign.ClientResponse resp;
        try {
            resp = clientUtilisateurFeign.creerClient(req);
        } catch (FeignException ex) {
            String body = ex.contentUTF8();
            String message = (body != null && !body.isBlank())
                    ? "Erreur service client: " + body
                    : "Erreur service client (" + ex.status() + ").";
            throw new IllegalArgumentException(message);
        }
        return resp.getId();
    }

    @Override
    public void modifierClient(Long clientId, String nom, String prenom, String email, String telephone, String cin,
                               String adresse, String sexe, String profession, String employeur,
                               LocalDate dateNaissance) {
        ClientUtilisateurFeign.ClientCreationRequest req = buildRequest(
                nom, prenom, email, telephone, cin, adresse, sexe, profession, employeur, dateNaissance
        );
        try {
            clientUtilisateurFeign.modifierClient(clientId, req);
        } catch (FeignException ex) {
            String body = ex.contentUTF8();
            String message = (body != null && !body.isBlank())
                    ? "Erreur mise à jour client: " + body
                    : "Erreur mise à jour client (" + ex.status() + ").";
            throw new IllegalArgumentException(message);
        }
    }
    @Override
    public ClientIdentityDto getClientIdentity(Long clientId) {
        ClientUtilisateurFeign.ClientIdentityResponse r = clientUtilisateurFeign.getClientIdentity(clientId);
        return new ClientIdentityDto(r.getId(), r.getNom(), r.getPrenom(), r.getCin(), r.getTelephone(), r.getEmail());
    }

    @Override
    public Long getClientIdByCin(String cin) {
        try {
            ClientUtilisateurFeign.ClientIdResponse resp = clientUtilisateurFeign.getClientIdByCin(cin);

            if (resp == null || resp.getId() == null) {
                throw new IllegalArgumentException("Client introuvable pour CIN: " + cin);
            }

            return resp.getId();

        } catch (FeignException ex) {
            if (ex.status() == 404 || ex.status() == 500) {
                throw new IllegalArgumentException("Client introuvable pour CIN: " + cin);
            }

            throw new IllegalArgumentException("Erreur service client: " + ex.getMessage());
        }
    }

    private ClientUtilisateurFeign.ClientCreationRequest buildRequest(
            String nom, String prenom, String email, String telephone, String cin,
            String adresse, String sexe, String profession, String employeur,
            LocalDate dateNaissance
    ) {
        ClientUtilisateurFeign.ClientCreationRequest req = new ClientUtilisateurFeign.ClientCreationRequest();
        req.setNom(nom);
        req.setPrenom(prenom);
        req.setEmail(email);
        req.setTelephone(telephone);
        req.setCin(cin);
        req.setAdresse(adresse);
        req.setSexe(sexe);
        req.setProfession(profession);
        req.setEmployeur(employeur);
        req.setDateNaissance(dateNaissance != null ? dateNaissance.toString() : null);
        return req;
    }
}
