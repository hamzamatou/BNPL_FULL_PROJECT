package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_demande.classes.DossierClient;

import java.util.List;
import java.util.Optional;

public interface DossierClientRepository extends JpaRepository<DossierClient, Long> {

    Optional<DossierClient> findByReferenceDossier(String referenceDossier);

    List<DossierClient> findByClientId(Long clientId);

    Optional<DossierClient> findTopByClientIdOrderByDateDerniereMiseAJourDesc(Long clientId);
}