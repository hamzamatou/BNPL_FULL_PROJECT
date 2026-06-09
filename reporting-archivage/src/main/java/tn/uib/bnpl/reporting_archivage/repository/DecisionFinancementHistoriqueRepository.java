package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tn.uib.bnpl.reporting_archivage.classes.DecisionFinancementHistorique;
import tn.uib.bnpl.reporting_archivage.classes.TypeDecisionFinancement;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DecisionFinancementHistoriqueRepository extends JpaRepository<DecisionFinancementHistorique, Long> {

    Page<DecisionFinancementHistorique> findByDemandeId(Long demandeId, Pageable pageable);

    Page<DecisionFinancementHistorique> findByDateDecisionBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DecisionFinancementHistorique> findByTypeDecisionAndDateDecisionBetween(
            TypeDecisionFinancement type, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DecisionFinancementHistorique> findByActeurUserId(Long acteurUserId, Pageable pageable);

    Page<DecisionFinancementHistorique> findByActeurUserIdAndDemandeId(
            Long acteurUserId, Long demandeId, Pageable pageable);

    Page<DecisionFinancementHistorique> findByActeurUserIdAndDateDecisionBetween(
            Long acteurUserId, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DecisionFinancementHistorique> findByActeurUserIdAndTypeDecisionAndDateDecisionBetween(
            Long acteurUserId, TypeDecisionFinancement type, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    long countByActeurUserIdAndDateDecisionAfter(Long acteurUserId, LocalDateTime since);

    long countByActeurUserIdAndTypeDecision(Long acteurUserId, TypeDecisionFinancement type);

    java.util.List<DecisionFinancementHistorique> findTop8ByActeurUserIdOrderByDateDecisionDesc(Long acteurUserId);

    long countByTypeDecision(TypeDecisionFinancement typeDecision);

    @Query("""
            select count(distinct d.demandeId)
            from DecisionFinancementHistorique d
            where d.typeDecision = :type
            """)
    long countDistinctDemandeIdByTypeDecision(@Param("type") TypeDecisionFinancement type);

    @Query("""
            select count(d)
            from DecisionFinancementHistorique d
            where d.typeDecision in :types
            """)
    long countByTypeDecisionIn(@Param("types") Collection<TypeDecisionFinancement> types);

    @Query(value = """
            SELECT COALESCE(acteur_email, 'Inconnu'),
                   SUM(CASE WHEN type_decision = 'ACCEPTEE' THEN 1 ELSE 0 END),
                   COUNT(*)
            FROM decision_financement_historique
            WHERE type_decision IN ('ACCEPTEE', 'REFUSEE')
            GROUP BY acteur_email
            ORDER BY COUNT(*) DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> acceptanceRateByActeur();
}
