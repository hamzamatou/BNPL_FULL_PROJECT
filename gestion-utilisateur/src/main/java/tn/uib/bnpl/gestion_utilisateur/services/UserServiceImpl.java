package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.config.JwtUtil;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Service
public class UserServiceImpl implements UserService {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailService = emailService;
    }

    // ======================
    // CREATE USER ADMIN
    // ======================
    @Override
    public User saveUser(User user) {

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        String rawPassword = user.getPassword() != null && !user.getPassword().isEmpty()
                ? user.getPassword()
                : generateTempPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(AccountStatus.CREATED);

        String token = UUID.randomUUID().toString();
        user.setActivationToken(token);
        user.setTokenExpiration(LocalDateTime.now().plusHours(24));

        User saved = userRepository.save(user);

        try {
            emailService.sendCredentialsEmail(
                saved.getEmail(),
                saved.getEmail(),
                rawPassword,
                token
            );
        } catch (Exception e) {
            System.out.println("Erreur email: " + e.getMessage());
        }
        return saved;
    }
    @Override
    public String login(String email, String password) throws Exception {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Email introuvable"));

        if (user.getStatus() == AccountStatus.BLOCKED) {
            throw new Exception("Compte bloqué");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new Exception("Mot de passe incorrect");
        }

        // Générer OTP 6 chiffres
        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        user.setOtpCode(otp);
        user.setOtpExpiration(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        // Envoyer OTP par email
        emailService.sendOtpEmail(user.getEmail(), otp);

        // Retourner signal "OTP requis" — pas encore de JWT
        return "OTP_SENT";
    }
    @Override
    public void sendOtp(String email) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Utilisateur introuvable"));

        String otp = String.format("%06d", (int)(Math.random() * 1000000));
        user.setOtpCode(otp);
        user.setOtpExpiration(LocalDateTime.now().plusMinutes(5));
        userRepository.save(user);

        emailService.sendOtpEmail(user.getEmail(), otp);
    }

    @Override
    public String verifyOtp(String email, String otpCode) throws Exception {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("Utilisateur introuvable"));

        if (user.getOtpCode() == null || !user.getOtpCode().equals(otpCode)) {
            throw new Exception("Code OTP invalide");
        }

        if (user.getOtpExpiration().isBefore(LocalDateTime.now())) {
            throw new Exception("Code OTP expiré");
        }

        // Invalider l'OTP après usage
        user.setOtpCode(null);
        user.setOtpExpiration(null);
        userRepository.save(user);

        return jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
    }

    // ======================
    // ACTIVATE ACCOUNT
    // ======================
    @Override
    public User activateAccount(String token, String newPassword, String confirmPassword) {

        if (!newPassword.equals(confirmPassword)) {
            throw new RuntimeException("Passwords not match");
        }

        User user = userRepository.findByActivationToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));

        if (user.getStatus() != AccountStatus.CREATED) {
            throw new RuntimeException("Déjà activé");
        }

        if (user.getTokenExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Token expiré");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setStatus(AccountStatus.ACTIVE);
        user.setActivationToken(UUID.randomUUID().toString());
        user.setTokenExpiration(LocalDateTime.now().plusHours(24));

        return userRepository.save(user);
    }

    // ======================
    // FIND BY TOKEN
    // ======================
    @Override
    public User findByToken(String token) {
        return userRepository.findByActivationToken(token)
                .orElseThrow(() -> new RuntimeException("Token invalide"));
    }

    // ======================
    // ENCODE PASSWORD
    // ======================
    @Override
    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }
    @Override
    public User toggleBlockUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setStatus(
                user.getStatus() == AccountStatus.BLOCKED
                        ? AccountStatus.ACTIVE
                        : AccountStatus.BLOCKED
        );

        return userRepository.save(user);
    }

    // ======================
    // CREATE CLIENT BNPL
    // ======================
    @Override
    public CreatedClientResponse createClientForBnpl(CreateClientRequest request) {

        if (userRepository.findByEmail(request.email().trim()).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User client = new User();
        client.setEmail(request.email().trim());
        client.setNom(trimToNull(request.nom()));
        client.setPrenom(trimToNull(request.prenom()));
        client.setCin(trimToNull(request.cin()));
        client.setTelephone(trimToNull(request.telephone()));
        client.setAdresse(trimToNull(request.adresse()));
        client.setSexe(trimToNull(request.sexe()));
        client.setProfession(trimToNull(request.profession()));
        client.setEmployeur(trimToNull(request.employeur()));

        client.setRole(Role.CLIENT);
        client.setStatus(AccountStatus.CREATED);

        User saved = userRepository.save(client);

        return new CreatedClientResponse(saved.getId(), saved.getEmail());
    }

    @Override
    public CreatedClientResponse updateClientForBnpl(Long clientId, CreateClientRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client introuvable"));

        if (client.getRole() != Role.CLIENT) {
            throw new RuntimeException("L'utilisateur " + clientId + " n'est pas un client");
        }

        String email = request.email() != null ? request.email().trim() : null;
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email client obligatoire");
        }

        userRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.getId().equals(clientId)) {
                throw new RuntimeException("Email déjà utilisé");
            }
        });

        client.setEmail(email);
        client.setNom(trimToNull(request.nom()));
        client.setPrenom(trimToNull(request.prenom()));
        client.setCin(trimToNull(request.cin()));
        client.setTelephone(trimToNull(request.telephone()));
        client.setAdresse(trimToNull(request.adresse()));
        client.setSexe(trimToNull(request.sexe()));
        client.setProfession(trimToNull(request.profession()));
        client.setEmployeur(trimToNull(request.employeur()));
        client.setDateModification(LocalDateTime.now());

        User saved = userRepository.save(client);
        return new CreatedClientResponse(saved.getId(), saved.getEmail());
    }

    // ======================
    // CLIENT IDENTITY
    // ======================
    @Override
    public ClientIdentityResponse getClientIdentity(Long clientId) {

        User u = userRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client introuvable"));

        return new ClientIdentityResponse(
                u.getId(),
                u.getNom(),
                u.getPrenom(),
                u.getCin(),
                u.getTelephone(),
                u.getEmail()
        );
    }

    // ======================
    // CLIENT BY CIN
    // ======================
    @Override
    public Long getClientIdByCin(String cin) {

        User u = userRepository.findByCin(cin)
                .orElseThrow(() -> new RuntimeException("Client introuvable"));

        return u.getId();
    }

    // ======================
    // UTIL
    // ======================
    private String generateTempPassword() {
        return "Bnpl@" + (int)(Math.random() * 100000);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ======================
    // OTHER
    // ======================
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
        user.setDateModification(LocalDateTime.now());
        return userRepository.save(user);
    }
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }
}