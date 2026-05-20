package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DossierArchiveRepository extends JpaRepository<DossierArchive, Long> {

    Optional<DossierArchive> findByDemandeId(Long demandeId);

    boolean existsByDemandeId(Long demandeId);

    Page<DossierArchive> findByDateArchivageBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DossierArchive> findByStatutFinal(String statutFinal, Pageable pageable);

    long countByDateArchivageAfter(LocalDateTime depuis);
}
