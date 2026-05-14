package tn.uib.bnpl.gestion_utilisateur.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT HS256 partagé avec les autres microservices (même {@code jwt.secret}).
 * Claims : {@code sub}=email, {@code id}=identifiant utilisateur, {@code role}=rôle métier.
 */
@Component
public class JwtUtil {

    private final Key key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret doit faire au moins 32 caractères (256 bits) pour HS256");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long userId, String email, Role role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("id", userId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 10))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractId(String token) {
        Object id = parseClaims(token).get("id");
        if (id == null) {
            return null;
        }
        if (id instanceof Number n) {
            return n.longValue();
        }
        if (id instanceof String s) {
            return Long.valueOf(s);
        }
        throw new IllegalStateException("Claim 'id' invalide dans le JWT");
    }

    public String extractRole(String token) {
        Object raw = parseClaims(token).get("role");
        if (raw == null) {
            return null;
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Lecture unique des claims (évite de parser le JWT plusieurs fois). */
    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}