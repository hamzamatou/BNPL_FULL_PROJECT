package tn.uib.bnpl.notification_service.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationActionLinkRepository extends JpaRepository<NotificationActionLink, Long> {

    Optional<NotificationActionLink> findByToken(String token);

    Optional<NotificationActionLink> findByTokenAndLinkType(String token, ActionLinkType linkType);
}
