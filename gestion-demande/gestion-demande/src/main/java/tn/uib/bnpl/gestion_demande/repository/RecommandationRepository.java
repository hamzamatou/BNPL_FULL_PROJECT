package tn.uib.bnpl.gestion_demande.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;
import tn.uib.bnpl.gestion_demande.classes.Recommandation;
import java.util.Optional;
 
public interface RecommandationRepository extends JpaRepository<Recommandation, Long> {
    Optional<Recommandation> findByDemandeId(Long demandeId);
}