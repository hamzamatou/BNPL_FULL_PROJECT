package tn.uib.bnpl.gestion_utilisateur.services;


import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.config.JwtUtil;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    @Override
    public User saveUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setDate_modification(LocalDateTime.now());
        return userRepository.save(user);
    }
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    @Override
    public User updateUser(User user) {
        user.setDate_modification(LocalDateTime.now());
        return userRepository.save(user);
    }

    @Override
    public User toggleBlockUser(Long id) {
        User user = userRepository.findById(id).orElseThrow();
        user.setStatut(!user.getStatut());
        return userRepository.save(user);
    }
    @Override
    public String login(String email, String password) throws Exception {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if(userOpt.isEmpty()) throw new Exception("Email non trouvé");

        User user = userOpt.get();

        if ("CLIENT".equalsIgnoreCase(user.getRole())) {
            throw new Exception("Les comptes client ne se connectent pas avec email/mot de passe (parcours BNPL).");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new Exception("Mot de passe non configuré pour ce compte");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new Exception("Mot de passe incorrect");
        }

        // JWT : id + email (sub) + role — utilisé par gestion-demande et le front
        return jwtUtil.generateToken(user.getId_user(), user.getEmail(), user.getRole());
    }

    @Override
    public CreatedClientResponse createClientForBnpl(CreateClientRequest request) {
        if (request.email() == null || request.email().isBlank()) {
            throw new IllegalArgumentException("Email obligatoire");
        }
        if (userRepository.findByEmail(request.email().trim()).isPresent()) {
            throw new IllegalStateException("Un utilisateur existe déjà avec cet email");
        }
        User client = new User(
                trimToNull(request.nom()),
                trimToNull(request.prenom()),
                trimToNull(request.cin()),
                request.email().trim(),
                trimToNull(request.adresse()),
                trimToNull(request.sexe()),
                trimToNull(request.profession()),
                trimToNull(request.employeur())
        );
        client.setTelephone(trimToNull(request.telephone()));
        // Client : pas de mot de passe (authentification via lien / OTP côté gestion-demande, pas ce login)
        client.setDate_modification(LocalDateTime.now());
        User saved = userRepository.save(client);
        return new CreatedClientResponse(saved.getId_user(), saved.getEmail());
    }

    @Override
    public ClientIdentityResponse getClientIdentity(Long clientId) {
        User u = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client introuvable: " + clientId));
        if (!"CLIENT".equalsIgnoreCase(u.getRole())) {
            throw new IllegalStateException("L'utilisateur " + clientId + " n'est pas un client BNPL");
        }
        return new ClientIdentityResponse(
                u.getId_user(),
                u.getNom(),
                u.getPrenom(),
                u.getCin(),
                u.getTelephone(),
                u.getEmail()
        );
    }

    @Override
    public Long getClientIdByCin(String cin) {
        if (cin == null || cin.isBlank()) {
            throw new IllegalArgumentException("CIN obligatoire");
        }
        String normalized = cin.trim();
        return userRepository.findByCin(normalized)
                .map(u -> {
                    if (!"CLIENT".equalsIgnoreCase(u.getRole())) {
                        throw new IllegalStateException("L'utilisateur " + u.getId_user() + " n'est pas un client BNPL");
                    }
                    return u.getId_user();
                })
                .orElse(null);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}