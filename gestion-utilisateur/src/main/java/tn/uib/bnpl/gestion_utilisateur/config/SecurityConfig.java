package tn.uib.bnpl.gestion_utilisateur.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiAuthenticationFilter apiAuthenticationFilter;

    public SecurityConfig(ApiAuthenticationFilter apiAuthenticationFilter) {
        this.apiAuthenticationFilter = apiAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .cors(cors -> {})

            // ❌ SUPPRIMÉ (cause 403)
            // .anonymous(anonymous -> anonymous.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/users/login",
                    "/api/users/register",
                    "/error" // ✅ IMPORTANT pour éviter blocage
                ).permitAll()
                .requestMatchers("/api/internal/**").authenticated()
                .anyRequest().authenticated()
            )

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())

            .addFilterBefore(apiAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }
}