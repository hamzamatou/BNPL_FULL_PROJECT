package tn.uib.bnpl.gestion_utilisateur.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByEmailAndPassword(String email, String password);

    Optional<User> findByCin(String cin);

    Optional<User> findByActivationToken(String activationToken);

    long countByRole(Role role);

    long countByRoleNot(Role role);

    long countByStatusAndRoleNot(AccountStatus status, Role role);

    List<User> findByRoleAndBanque_IdAndStatus(Role role, Long banqueId, AccountStatus status);

    List<User> findByRole(Role role);
}
