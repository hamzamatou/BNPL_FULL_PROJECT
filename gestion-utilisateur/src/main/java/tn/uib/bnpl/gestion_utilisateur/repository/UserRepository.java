package tn.uib.bnpl.gestion_utilisateur.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.uib.bnpl.gestion_utilisateur.classes.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndPassword(String email, String password);
    Optional<User> findByCin(String cin);

    /**
     * Évite le nom {@code existsByRole} / {@code countByRole} (parseur Spring Data 4 → erreur sur {@code id}).
     */
    @Query("select count(u) from User u where u.role = :role")
    long countUsersWithRole(@Param("role") String role);
}