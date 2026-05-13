package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;

import java.util.List;
import java.util.Optional;

public interface DemandeFinancementRepository extends JpaRepository<DemandeFinancement, Long> {

    Optional<DemandeFinancement> findByReferenceDemande(String referenceDemande);

    // toutes les demandes d'un dossier (utile si tu connais dossierClientId)
    List<DemandeFinancement> findByDossierClientId(Long dossierClientId);

    // toutes les demandes par statut (Sprint 1 / liste banque simple)
    List<DemandeFinancement> findByStatut(String statut);

    // US09: toutes les demandes créées par le commerçant
    // (correspond au champ DemandeFinancement.commercantUserId)
    List<DemandeFinancement> findByCommercantUserId(Long commercantUserId);

    @Query("""
            select distinct d
            from DemandeFinancement d
            left join fetch d.dossierClient dc
            left join fetch dc.documents docs
            where d.id = :id
            """)
    Optional<DemandeFinancement> findCompleteById(@Param("id") Long id);
}