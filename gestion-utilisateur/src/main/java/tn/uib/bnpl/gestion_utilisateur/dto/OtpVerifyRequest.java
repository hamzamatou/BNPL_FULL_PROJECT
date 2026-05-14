package tn.uib.bnpl.gestion_utilisateur.dto;

public record OtpVerifyRequest(String email, String otpCode) {}