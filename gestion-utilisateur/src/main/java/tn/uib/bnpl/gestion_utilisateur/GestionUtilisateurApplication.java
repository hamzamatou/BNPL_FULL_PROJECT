package tn.uib.bnpl.gestion_utilisateur;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * Sans exclusion : Spring Boot enregistre {@code BearerTokenAuthenticationFilter} (OAuth2 Resource Server).
 * Il s’exécute après {@link tn.uib.bnpl.gestion_utilisateur.config.ApiAuthenticationFilter} et écrase
 * l’auth {@code INTERNAL} (clé API) par le JWT → 403 sur {@code /api/internal/**}.
 * L’auth JWT est entièrement gérée par {@code ApiAuthenticationFilter} + {@code JwtUtil} (JJWT).
 */
@SpringBootApplication
public class GestionUtilisateurApplication {

	public static void main(String[] args) {
		SpringApplication.run(GestionUtilisateurApplication.class, args);
	}

}
