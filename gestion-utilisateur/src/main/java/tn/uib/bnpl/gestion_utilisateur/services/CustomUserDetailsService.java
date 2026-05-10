package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

/**
 * Mot de passe absent pour les {@code CLIENT} : on fournit un hash factice pour satisfaire l’API
 * {@link UserDetails} (aucun mot de passe utilisateur ne peut correspondre).
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final String INTERNAL_PLACEHOLDER = "__CLIENT_ACCOUNT_NO_PASSWORD_LOGIN__";
    private final String bcryptPlaceholderForNullPassword;

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bcryptPlaceholderForNullPassword = passwordEncoder.encode(INTERNAL_PLACEHOLDER);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur non trouvé avec email: " + email));

        String passwordForSpring = user.getPassword();
        if (passwordForSpring == null || passwordForSpring.isBlank()) {
            passwordForSpring = bcryptPlaceholderForNullPassword;
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(passwordForSpring)
                .authorities(user.getRole())
                .accountLocked(!user.getStatut())
                .build();
    }
}
