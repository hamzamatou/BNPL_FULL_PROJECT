package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.reporting_archivage.classes.DecisionFinancementHistorique;
import tn.uib.bnpl.reporting_archivage.classes.TypeDecisionFinancement;

import java.time.LocalDateTime;

public interface DecisionFinancementHistoriqueRepository extends JpaRepository<DecisionFinancementHistorique, Long> {

    Page<DecisionFinancementHistorique> findByDemandeId(Long demandeId, Pageable pageable);

    Page<DecisionFinancementHistorique> findByDateDecisionBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DecisionFinancementHistorique> findByTypeDecisionAndDateDecisionBetween(
            TypeDecisionFinancement type, LocalDateTime debut, LocalDateTime fin, Pageable pageable);
}
