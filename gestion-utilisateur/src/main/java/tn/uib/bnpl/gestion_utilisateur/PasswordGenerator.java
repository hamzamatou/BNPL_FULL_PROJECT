package tn.uib.bnpl.gestion_utilisateur;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashed = encoder.encode("superadmin123");
        System.out.println(hashed);
    }
}

