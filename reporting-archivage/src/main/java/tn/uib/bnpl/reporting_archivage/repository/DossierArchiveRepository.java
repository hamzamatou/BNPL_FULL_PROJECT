package tn.uib.bnpl.reporting_archivage.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface DossierArchiveRepository extends JpaRepository<DossierArchive, Long> {

    Optional<DossierArchive> findByDemandeId(Long demandeId);

    boolean existsByDemandeId(Long demandeId);

    Page<DossierArchive> findByDateArchivageBetween(
            LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DossierArchive> findByStatutFinal(String statutFinal, Pageable pageable);

    long countByDateArchivageAfter(LocalDateTime depuis);

    long countByStatutFinalIgnoreCase(String statutFinal);

    @Query("select coalesce(sum(d.montant), 0) from DossierArchive d")
    BigDecimal sumMontantArchive();

    @Query("""
            select upper(d.statutFinal), count(d)
            from DossierArchive d
            where d.statutFinal is not null
            group by upper(d.statutFinal)
            """)
    java.util.List<Object[]> countGroupedByStatutFinal();
}
