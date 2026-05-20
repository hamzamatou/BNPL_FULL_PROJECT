package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.reporting_archivage.classes.AccesPlateformeHistorique;

import java.time.LocalDateTime;

public interface AccesPlateformeHistoriqueRepository extends JpaRepository<AccesPlateformeHistorique, Long> {

    Page<AccesPlateformeHistorique> findByDateAccesBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<AccesPlateformeHistorique> findBySuspectTrueAndDateAccesBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<AccesPlateformeHistorique> findByUserIdAndDateAccesBetween(
            Long userId, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    long countBySuspectTrueAndDateAccesAfter(LocalDateTime depuis);
}
