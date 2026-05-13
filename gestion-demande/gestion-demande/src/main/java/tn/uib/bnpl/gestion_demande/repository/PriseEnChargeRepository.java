package tn.uib.bnpl.gestion_demande.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
            select count(p)
            from PriseEnCharge p
            where p.demande.id = :demandeId
              and p.statut = 'VERROUILLEE'
              and p.dateExpiration > :now
            """)
    long countActiveVerrouillages(
            @Param("demandeId") Long demandeId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select p.demande
            from PriseEnCharge p
            where p.banqueUserId = :banqueUserId
              and p.statut = 'ROUTE'
              and p.demande.statut = 'SOUMISE'
              and not exists (
                  select 1
                  from PriseEnCharge v
                  where v.demande.id = p.demande.id
                    and v.statut = 'VERROUILLEE'
                    and v.dateExpiration > :now
              )
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
              and p.dateExpiration > :now
            """)
    Optional<PriseEnCharge> findActiveVerrouillage(
            @Param("demandeId") Long demandeId,
            @Param("banqueUserId") Long banqueUserId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select distinct p.demande
            from PriseEnCharge p
            where p.banqueUserId = :banqueUserId
              and p.statut = 'VERROUILLEE'
              and p.dateExpiration > :now
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
}
