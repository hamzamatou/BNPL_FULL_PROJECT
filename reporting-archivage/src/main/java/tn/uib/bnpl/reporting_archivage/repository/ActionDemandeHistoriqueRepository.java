package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.reporting_archivage.classes.ActionDemandeHistorique;
import tn.uib.bnpl.reporting_archivage.classes.TypeActionDemande;

import java.time.LocalDateTime;
import java.util.List;

public interface ActionDemandeHistoriqueRepository extends JpaRepository<ActionDemandeHistorique, Long> {

    Page<ActionDemandeHistorique> findByDemandeId(Long demandeId, Pageable pageable);

    Page<ActionDemandeHistorique> findByDateActionBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<ActionDemandeHistorique> findByTypeActionAndDateActionBetween(
            TypeActionDemande type, LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    long countByDateActionAfter(LocalDateTime depuis);

    List<ActionDemandeHistorique> findTop10ByOrderByDateActionDesc();
}
