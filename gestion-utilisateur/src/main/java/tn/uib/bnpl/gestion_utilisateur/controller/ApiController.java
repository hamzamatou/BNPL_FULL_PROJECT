package tn.uib.bnpl.gestion_utilisateur.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Banque;
import tn.uib.bnpl.gestion_utilisateur.classes.CreateAnalysteRequest;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.config.JwtUtil;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.OtpVerifyRequest;
import tn.uib.bnpl.gestion_utilisateur.repository.BanqueRepository;
import tn.uib.bnpl.gestion_utilisateur.services.UserService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:4200", "http://localhost:8081"})
public class ApiController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    private final BanqueRepository banqueRepository;

    public ApiController(UserService userService,
                         JwtUtil jwtUtil,
                         BanqueRepository banqueRepository) {

        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.banqueRepository = banqueRepository;
    }
    @PostMapping("/users/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {

        try {
            String email = body.get("email");
            String password = body.get("password");

            User user = userService.findByEmail(email);

            if (user.getStatus() == AccountStatus.BLOCKED)
                return ResponseEntity.status(403).body(Map.of("error", "Compte bloqué"));

            // vérifie password + envoie OTP
            userService.login(email, password);

            // 🔥 AJOUT TOKEN ICI
            String token = jwtUtil.generateToken(
                    user.getId(),
                    user.getEmail(),
                    user.getRole()
            );

            Map<String, Object> resp = new HashMap<>();
            resp.put("otpRequired", true);
            resp.put("email", email);

            // 🔥 AJOUT ROLE + TOKEN
            resp.put("token", token);
            resp.put("role", user.getRole());

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    // Nouveau endpoint : vérifier OTP
    @PostMapping("/users/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody OtpVerifyRequest request) {
        try {
            String token = userService.verifyOtp(request.email(), request.otpCode());

            User user = userService.findByEmail(request.email());

            Map<String, Object> resp = new HashMap<>();
            resp.put("token", token);
            resp.put("role", user.getRole());
            resp.put("status", user.getStatus());

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
    @PostMapping("/users/activate")
    public ResponseEntity<?> activate(@RequestBody Map<String, String> body) {

        try {
            String token = body.get("token");
            String password = body.get("password");
            String confirm = body.get("confirmPassword");

            User user = userService.activateAccount(token, password, confirm);

            return ResponseEntity.ok(Map.of(
                    "message", "Compte activé avec succès"
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        }
    }
    @PostMapping("/users/register")
    public ResponseEntity<User> register(@RequestBody User user) {
        return ResponseEntity.ok(userService.saveUser(user));
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<User> updateUser(@PathVariable("id") Long id, @RequestBody User user) {
    	user.setId(id);
        return ResponseEntity.ok(userService.updateUser(user));
    }
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé"));
    }
    @PutMapping("/users/toggle/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<User> toggleBlockUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.toggleBlockUser(id));
    }

    @PostMapping("/clients")
    @PreAuthorize("hasAuthority('COMMERCANT') or hasAuthority('ADMIN')")
    public ResponseEntity<CreatedClientResponse> createClient(@RequestBody CreateClientRequest request) {
        return ResponseEntity.ok(userService.createClientForBnpl(request));
    }

    /**
     * Création client depuis gestion-demande (Feign) : {@code X-Internal-Api-Key}, sans JWT commerçant.
     */
    @PostMapping("/internal/clients")
    @PreAuthorize("hasAuthority('INTERNAL')")
    public ResponseEntity<CreatedClientResponse> createClientInternal(@RequestBody CreateClientRequest request) {
        return ResponseEntity.ok(userService.createClientForBnpl(request));
    }

    @GetMapping("/internal/clients/{id}/identity")
    @PreAuthorize("hasAuthority('INTERNAL')")
    public ResponseEntity<ClientIdentityResponse> getClientIdentity(@PathVariable("id") Long id) {
        return ResponseEntity.ok(userService.getClientIdentity(id));
    }
    // Récupération de l'id client (rôle CLIENT) depuis le CIN
    @GetMapping("/internal/clients/by-cin")
    @PreAuthorize("hasAuthority('INTERNAL')")
    public ResponseEntity<Map<String, Long>> getClientIdByCin(@RequestParam("cin") String cin) {
        Long id = userService.getClientIdByCin(cin);
        return ResponseEntity.ok(Map.of("id", id));
    }
    @PostMapping("/analystes")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<User> createAnalyste(@RequestBody CreateAnalysteRequest data) {

        Banque banque;

        // =========================
        // 1. CHECK BANQUE EXISTE
        // =========================
        if (data.banqueId != null) {

            banque = banqueRepository.findById(data.banqueId)
                    .orElseThrow(() -> new RuntimeException("Banque introuvable"));

        } else {

            // 2. CREATE NEW BANQUE
            Banque newBanque = new Banque();
            newBanque.setNomBanque(data.nomBanque);
            newBanque.setCodeBanque(data.codeBanque);
            newBanque.setEmail(data.banqueEmail);
            newBanque.setTelephone(data.banqueTelephone);
            newBanque.setAdresse(data.banqueAdresse);

            banque = banqueRepository.save(newBanque);
        }

        // =========================
        // 3. CREATE USER
        // =========================
        User user = new User();
        user.setNom(data.nom);
        user.setPrenom(data.prenom);
        user.setEmail(data.email);
        user.setTelephone(data.telephone);
        user.setPassword(data.password);
        user.setPoste(data.poste);
        user.setRole(Role.ANALYSTE_BANCAIRE);
        user.setBanque(banque);

        return ResponseEntity.ok(userService.saveUser(user));
    }
}
