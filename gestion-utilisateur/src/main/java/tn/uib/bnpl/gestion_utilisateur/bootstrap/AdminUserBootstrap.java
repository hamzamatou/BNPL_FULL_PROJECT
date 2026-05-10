package tn.uib.bnpl.gestion_utilisateur.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;
import tn.uib.bnpl.gestion_utilisateur.services.UserService;

/**
 * Crée un premier compte {@code ADMIN} si la base n’en contient aucun (tests / dev).
 * Désactiver ou changer les identifiants en production ({@code app.admin.bootstrap.*}).
 */
@Component
@Order(100)
@ConditionalOnProperty(name = "app.admin.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class AdminUserBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String adminEmail;
    private final String adminPassword;

    public AdminUserBootstrap(
            UserRepository userRepository,
            UserService userService,
            @Value("${app.admin.bootstrap.email:admin@uib.bnpl}") String adminEmail,
            @Value("${app.admin.bootstrap.password:ChangeMeAdmin2026!}") String adminPassword) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.countUsersWithRole("ADMIN") > 0) {
            log.debug("Au moins un ADMIN existe déjà — pas de création automatique.");
            return;
        }
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.warn("app.admin.bootstrap.email/password vides — aucun ADMIN créé.");
            return;
        }
        if (userRepository.findByEmail(adminEmail.trim()).isPresent()) {
            log.warn("Email {} déjà utilisé — aucun ADMIN bootstrap (ajoutez role=ADMIN manuellement si besoin).", adminEmail);
            return;
        }

        User admin = new User();
        admin.setNom("Administrateur");
        admin.setPrenom("UIB");
        admin.setEmail(adminEmail.trim());
        admin.setPassword(adminPassword);
        admin.setRole("ADMIN");
        admin.setStatut(true);

        userService.saveUser(admin);
        log.info("Compte ADMIN initial créé : {} — changez le mot de passe en production.", adminEmail);
    }
}
