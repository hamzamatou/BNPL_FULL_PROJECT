package tn.uib.bnpl.gestion_utilisateur.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.config.JwtUtil;
import tn.uib.bnpl.gestion_utilisateur.client.NotificationServiceClient;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.NotificationEmailRequest;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final NotificationPublisher notificationPublisher;
    private final NotificationServiceClient notificationClient;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.notifications.async.enabled:false}")
    private boolean asyncNotificationsEnabled;

    @Value("${internal.api.key}")
    private String internalApiKey;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil,
                           NotificationPublisher notificationPublisher,
                           NotificationServiceClient notificationClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.notificationPublisher = notificationPublisher;
        this.notificationClient = notificationClient;
    }

    // ======================
    // CREATE USER ADMIN
    // ======================
    @Override
    public User saveUser(User user) {

        String normalizedEmail = normalizeEmail(user.getEmail());
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            throw new RuntimeException("Email obligatoire");
        }
        user.setEmail(normalizedEmail);

        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        String rawPassword = user.getPassword() != null && !user.getPassword().isEmpty()
                ? user.getPassword()
                : generateTempPassword();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(AccountStatus.CREATED);

        User saved = userRepository.save(user);

        publishActivationEmail(saved.getId(), saved.getEmail());
        return saved;
    }
    @Override
    public String login(String email, String password) throws Exception {

        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new Exception("Email introuvable"));

        if (user.getStatus() == AccountStatus.BLOCKED) {
            throw new Exception("Compte bloqué");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new Exception("Mot de passe incorrect");
        }

        publishLoginOtpEmail(user.getEmail());

        return "OTP_SENT";
    }
    @Override
    public void sendOtp(String email) throws Exception {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new Exception("Utilisateur introuvable"));

        if (user.getStatus() == AccountStatus.BLOCKED) {
            throw new Exception("Compte bloqué");
        }

        publishLoginOtpEmail(user.getEmail());
    }

    private void publishActivationEmail(Long userId, String email) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async desactivees: mail activation non publie (userId={})", userId);
            return;
        }
        NotificationEmailRequest event = new NotificationEmailRequest(
                UUID.randomUUID().toString(),
                "user-" + userId,
                internalApiKey,
                email,
                "ACTIVATION_ACCOUNT",
                Map.of("userId", userId, "email", email),
                LocalDateTime.now()
        );
        try {
            notificationPublisher.publishEmail(event);
        } catch (Exception ex) {
            log.error("Publication RabbitMQ activation echouee (userId={})", userId, ex);
            throw new IllegalStateException("Impossible de publier la notification activation", ex);
        }
    }

    private void publishLoginOtpEmail(String email) {
        if (!asyncNotificationsEnabled) {
            log.warn("Notifications async desactivees: mail OTP login non publie (email={})", email);
            return;
        }
        NotificationEmailRequest event = new NotificationEmailRequest(
                UUID.randomUUID().toString(),
                email,
                internalApiKey,
                email,
                "LOGIN_OTP",
                Map.of("email", email),
                LocalDateTime.now()
        );
        try {
            notificationPublisher.publishEmail(event);
        } catch (Exception ex) {
            log.error("Publication RabbitMQ OTP login echouee (email={})", email, ex);
            throw new IllegalStateException("Impossible de publier la notification OTP", ex);
        }
    }

    @Override
    public String verifyOtp(String email, String otpCode) throws Exception {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new Exception("Utilisateur introuvable"));

        try {
            notificationClient.verifyLoginOtp(email, otpCode.trim());
        } catch (IllegalArgumentException ex) {
            throw new Exception(ex.getMessage());
        }

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

        NotificationServiceClient.ActivationResolve resolved;
        try {
            resolved = notificationClient.resolveActivation(token);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex.getMessage());
        }

        User user = userRepository.findById(resolved.userId())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (user.getStatus() != AccountStatus.CREATED) {
            throw new RuntimeException("Déjà activé");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setStatus(AccountStatus.ACTIVE);
        user.setActivationToken(null);
        user.setTokenExpiration(null);
        User saved = userRepository.save(user);
        notificationClient.consumeActivation(token);
        return saved;
    }

    // ======================
    // FIND BY TOKEN
    // ======================
    @Override
    public User findByToken(String token) {
        NotificationServiceClient.ActivationResolve resolved;
        try {
            resolved = notificationClient.resolveActivation(token);
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException(ex.getMessage());
        }
        return userRepository.findById(resolved.userId())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
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

        if (userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).isPresent()) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User client = new User();
        client.setEmail(normalizeEmail(request.email()));
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

        String email = normalizeEmail(request.email());
        if (email == null || email.isBlank()) {
            throw new RuntimeException("Email client obligatoire");
        }

        userRepository.findByEmailIgnoreCase(email).ifPresent(existing -> {
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
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}