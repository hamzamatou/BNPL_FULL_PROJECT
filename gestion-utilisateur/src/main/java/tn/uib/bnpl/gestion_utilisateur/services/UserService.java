package tn.uib.bnpl.gestion_utilisateur.services;

import java.util.List;


import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;



public interface UserService {

    User saveUser(User user);

    List<User> getAllUsers();

    void deleteUser(Long id);

	User updateUser(User user);

	User toggleBlockUser(Long id);

	String login(String email, String password) throws Exception;

	CreatedClientResponse createClientForBnpl(CreateClientRequest request);

	ClientIdentityResponse getClientIdentity(Long clientId);

	/**
	 * Récupère l'id utilisateur (rôle CLIENT) à partir du CIN.
	 * {@code null} si aucun utilisateur avec ce CIN (gestion-demande crée alors le client).
	 * {@link IllegalStateException} si le CIN existe mais le compte n'est pas un CLIENT BNPL.
	 */
	Long getClientIdByCin(String cin);

}
