package tn.uib.bnpl.gestion_utilisateur.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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

    // 🔥 CORS GLOBAL (IMPORTANT)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("http://localhost:4200"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))

            // 🔥 IMPORTANT
            .cors(cors -> {})

            .authorizeHttpRequests(auth -> auth

                // 🔥 PRE-FLIGHT CORS MUST BE ALLOWED
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                .requestMatchers(HttpMethod.POST,
                    "/api/users/login",
                    "/api/users/register",
                    "/api/users/activate",
                    "/api/users/verify-otp",
                    "/api/users/resend-otp"
                ).permitAll()
                .requestMatchers("/error").permitAll()

                // internal APIs
                .requestMatchers("/api/internal/**").authenticated()

                .anyRequest().authenticated()
            )

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())

            // 🔥 IMPORTANT: JWT filter must ignore OPTIONS (voir note plus bas)
            .addFilterBefore(apiAuthenticationFilter, AuthorizationFilter.class);

        return http.build();
    }
}