package tn.uib.bnpl.gestion_utilisateur.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.uib.bnpl.gestion_utilisateur.classes.Banque;

public interface BanqueRepository extends JpaRepository<Banque, Long> {
}
