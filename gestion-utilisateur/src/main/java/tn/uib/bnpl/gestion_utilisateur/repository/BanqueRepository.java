package tn.uib.bnpl.gestion_utilisateur.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_utilisateur.classes.Banque;

import java.util.Optional;

public interface BanqueRepository extends JpaRepository<Banque, Long> {

    Optional<Banque> findByCodeBanqueIgnoreCase(String codeBanque);
}
