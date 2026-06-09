package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DemandeFinancementRepository extends JpaRepository<DemandeFinancement, Long> {

    long countByStatutIgnoreCase(String statut);

    @Query("""
            select coalesce(sum(d.montant), 0)
            from DemandeFinancement d
            """)
    BigDecimal sumMontantActif();

    Optional<DemandeFinancement> findByReferenceDemande(String referenceDemande);

    // toutes les demandes d'un dossier (utile si tu connais dossierClientId)
    List<DemandeFinancement> findByDossierClientId(Long dossierClientId);

    // toutes les demandes par statut (Sprint 1 / liste banque simple)
    List<DemandeFinancement> findByStatut(String statut);

    // US09: toutes les demandes créées par le commerçant
    // (correspond au champ DemandeFinancement.commercantUserId)
    // join fetch dossierClient pour éviter LazyInitializationException
    // lors du mapping DTO hors session Hibernate.
    @Query("""
            select d
            from DemandeFinancement d
            left join fetch d.dossierClient dc
            where d.commercantUserId = :commercantUserId
            order by d.dateCreation desc
            """)
    List<DemandeFinancement> findByCommercantUserId(@Param("commercantUserId") Long commercantUserId);

    @Query("""
            select distinct d
            from DemandeFinancement d
            left join fetch d.dossierClient dc
            left join fetch dc.documents docs
            left join fetch d.recommandation reco
            left join fetch d.prescoringScore ps
            where d.id = :id
            """)
    Optional<DemandeFinancement> findCompleteById(@Param("id") Long id);

    /** Workers Camunda (hors thread HTTP) : évite LazyInitializationException sur dossierClient. */
    @Query("""
            select d
            from DemandeFinancement d
            join fetch d.dossierClient dc
            left join fetch d.prescoringScore ps
            where d.id = :id
            """)
    Optional<DemandeFinancement> findByIdForWorkflow(@Param("id") Long id);

    /** Liste admin : toutes les demandes actives en base gestion-demande. */
    @Query("""
            select d
            from DemandeFinancement d
            left join fetch d.dossierClient dc
            order by d.dateCreation desc
            """)
    List<DemandeFinancement> findAllWithDossierForAdmin();

    @Query("""
            select d.statut, count(d)
            from DemandeFinancement d
            where d.statut is not null
            group by d.statut
            """)
    List<Object[]> countGroupedByStatut();

    long countByDateCreationGreaterThanEqual(LocalDateTime since);

    @Query("""
            select d.dateCreation
            from DemandeFinancement d
            where d.dateCreation is not null and d.dateCreation >= :since
            """)
    List<LocalDateTime> findCreationDatesSince(@Param("since") LocalDateTime since);

    @Query("""
            select d.commercantUserId, count(d)
            from DemandeFinancement d
            where d.commercantUserId is not null
            group by d.commercantUserId
            order by count(d) desc
            """)
    List<Object[]> countGroupedByCommercantUserId();
}