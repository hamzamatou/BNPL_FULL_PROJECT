package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;


@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }


    public void sendCredentialsEmail(String to, String email, String password, String token) {

    	String link = "http://localhost:4200/activate-account/" + token;
        String message =
                "Bonjour,\n\n" +
                "Votre compte a été créé.\n\n" +
                "Identifiants :\n" +
                "Email : " + email + "\n" +
                "Mot de passe temporaire : " + password + "\n\n" +
                "Lien d'activation :\n" +
                link + "\n\n" +
                "Vous devez activer votre compte lors de la première connexion.";

        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("Activation compte BNPL");
        mail.setText(message);

        mailSender.send(mail);
    }
    public void sendOtpEmail(String to, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Votre code de vérification UIB BNPL");
        message.setText(
            "Bonjour,\n\n" +
            "Votre code de vérification est : " + otpCode + "\n\n" +
            "Ce code est valable pendant 5 minutes.\n" +
            "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n\n" +
            "UIB - Plateforme BNPL"
        );
        mailSender.send(message);
    }
}
