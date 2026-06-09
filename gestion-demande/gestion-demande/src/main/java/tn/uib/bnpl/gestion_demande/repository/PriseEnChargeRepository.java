package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriseEnChargeRepository extends JpaRepository<PriseEnCharge, Long> {

    Optional<PriseEnCharge> findByDemandeIdAndBanqueUserId(Long demandeId, Long banqueUserId);

    Optional<PriseEnCharge> findByDemandeIdAndBanqueUserIdAndStatut(Long demandeId, Long banqueUserId, String statut);

    /**
     * Verrou actif : fenêtre 48 h (decision null + dateExpiration) ou instruction en cours (EN_COURS).
     */
    @Query("""
            select count(p)
            from PriseEnCharge p
            where p.demande.id = :demandeId
              and p.statut = 'VERROUILLEE'
              and (
                  (p.decision is null and p.dateExpiration > :now)
                  or p.decision = 'EN_COURS'
              )
            """)
    long countActiveVerrouillages(
            @Param("demandeId") Long demandeId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select distinct d
            from PriseEnCharge p
            join p.demande d
            left join fetch d.dossierClient dc
            where p.banqueUserId = :banqueUserId
              and p.statut = 'ROUTE'
              and d.statut = 'SOUMISE'
              and not exists (
                  select 1
                  from PriseEnCharge v
                  where v.demande.id = d.id
                    and v.statut = 'VERROUILLEE'
                    and (
                        (v.decision is null and v.dateExpiration > :now)
                        or v.decision = 'EN_COURS'
                    )
              )
            order by d.dateDerniereMiseAJour desc
            """)
    List<DemandeFinancement> findDemandesBanqueNonVerrouillees(
            @Param("banqueUserId") Long banqueUserId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select p
            from PriseEnCharge p
            where p.demande.id = :demandeId
              and p.banqueUserId = :banqueUserId
              and p.statut = 'VERROUILLEE'
              and (
                  (p.decision is null and p.dateExpiration > :now)
                  or p.decision = 'EN_COURS'
              )
            """)
    Optional<PriseEnCharge> findActiveVerrouillage(
            @Param("demandeId") Long demandeId,
            @Param("banqueUserId") Long banqueUserId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select distinct d
            from PriseEnCharge p
            join p.demande d
            left join fetch d.dossierClient dc
            where p.banqueUserId = :banqueUserId
              and p.statut = 'VERROUILLEE'
              and (
                  (p.decision is null and p.dateExpiration > :now)
                  or p.decision = 'EN_COURS'
              )
            order by d.dateDerniereMiseAJour desc
            """)
    List<DemandeFinancement> findDemandesAffecteesVerrouilleesPourBanque(
            @Param("banqueUserId") Long banqueUserId,
            @Param("now") LocalDateTime now
    );

    /**
     * Banques encore en ROUTE sur la demande (après refus partiel, les lignes refusées passent en DEVERROUILLEE).
     */
    @Query("""
            select count(p)
            from PriseEnCharge p
            where p.demande.id = :demandeId
              and p.statut = 'ROUTE'
            """)
    long countRouteBanksNotRefused(@Param("demandeId") Long demandeId);

    @Query("""
            select p
            from PriseEnCharge p
            where p.demande.id = :demandeId
              and p.statut = 'VERROUILLEE'
              and p.decision is null
              and p.dateExpiration is not null
              and p.dateExpiration <= :now
            """)
    Optional<PriseEnCharge> findFenetre48hExpiree(
            @Param("demandeId") Long demandeId,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("delete from PriseEnCharge p where p.demande.id = :demandeId")
    void deleteByDemandeId(@Param("demandeId") Long demandeId);
}
