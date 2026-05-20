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
    User findByEmail(String email);

    User updateUser(User user);

    User toggleBlockUser(Long id);

    String login(String email, String password) throws Exception;

    User activateAccount(String token, String newPassword, String confirmPassword);

    CreatedClientResponse createClientForBnpl(CreateClientRequest request);

    CreatedClientResponse updateClientForBnpl(Long clientId, CreateClientRequest request);

    ClientIdentityResponse getClientIdentity(Long clientId);

    Long getClientIdByCin(String cin);

    User findByToken(String token);

    String encodePassword(String password);
    void sendOtp(String email) throws Exception;
    String verifyOtp(String email, String otpCode) throws Exception;
}