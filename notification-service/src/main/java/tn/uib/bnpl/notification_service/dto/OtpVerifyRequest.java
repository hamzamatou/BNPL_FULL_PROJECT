package tn.uib.bnpl.notification_service.dto;

public record OtpVerifyRequest(
        String context,
        String email,
        String linkToken,
        String otp
) {}
