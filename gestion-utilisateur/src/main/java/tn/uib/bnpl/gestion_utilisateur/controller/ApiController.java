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

import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.config.JwtUtil;
import tn.uib.bnpl.gestion_utilisateur.dto.ClientIdentityResponse;
import tn.uib.bnpl.gestion_utilisateur.dto.CreateClientRequest;
import tn.uib.bnpl.gestion_utilisateur.dto.CreatedClientResponse;
import tn.uib.bnpl.gestion_utilisateur.services.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d’entrée HTTP unique : utilisateurs, clients BNPL, endpoints internes (clé API).
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = {"http://localhost:4200", "http://localhost:8081"})
public class ApiController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public ApiController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }
    @PostMapping("/users/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            String email = body.get("email");
            String password = body.get("password");
            String token = userService.login(email, password);
            String role = jwtUtil.extractRole(token);
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "role", role,
                    "message", "Login réussi"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
        user.setId_user(id);
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
        if (id == null) {
            Map<String, Long> body = new HashMap<>(1);
            body.put("id", null);
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(Map.of("id", id));
    }
}
