package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.reporting_archivage.classes.ActionDocumentHistorique;

import java.time.LocalDateTime;

public interface ActionDocumentHistoriqueRepository extends JpaRepository<ActionDocumentHistorique, Long> {

    Page<ActionDocumentHistorique> findByDemandeId(Long demandeId, Pageable pageable);

    Page<ActionDocumentHistorique> findByObjectKey(String objectKey, Pageable pageable);

    Page<ActionDocumentHistorique> findByDateActionBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);
}
