package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_demande.classes.ActionClientToken;

import java.util.Optional;

public interface ActionClientTokenRepository extends JpaRepository<ActionClientToken, Long> {

    Optional<ActionClientToken> findByToken(String token);
}