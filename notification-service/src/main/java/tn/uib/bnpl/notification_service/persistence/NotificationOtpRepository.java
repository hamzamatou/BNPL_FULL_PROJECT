package tn.uib.bnpl.notification_service.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationOtpRepository extends JpaRepository<NotificationOtp, Long> {

    Optional<NotificationOtp> findTopByEmailAndContextAndUsedFalseOrderByCreatedAtDesc(
            String email, OtpContext context);

    Optional<NotificationOtp> findTopByLinkTokenAndContextAndUsedFalseOrderByCreatedAtDesc(
            String linkToken, OtpContext context);
}
