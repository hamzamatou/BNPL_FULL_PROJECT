package tn.uib.bnpl.gestion_demande.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;
import java.util.Optional;
 
public interface PrescoringScoreRepository extends JpaRepository<PrescoringScore, Long> {
    Optional<PrescoringScore> findByDemandeId(Long demandeId);
}