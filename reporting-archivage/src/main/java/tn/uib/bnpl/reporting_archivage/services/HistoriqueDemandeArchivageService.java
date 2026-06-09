package tn.uib.bnpl.reporting_archivage.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.ActionDemandeHistorique;
import tn.uib.bnpl.reporting_archivage.classes.TypeActionDemande;
import tn.uib.bnpl.reporting_archivage.repository.ActionDemandeHistoriqueRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Historique des actions : enregistré en temps réel via {@code /api/internal/audit}.
 * À l'archivage, seul l'événement de clôture est ajouté ici.
 */
@Service
public class HistoriqueDemandeArchivageService {

    private static final Logger log = LoggerFactory.getLogger(HistoriqueDemandeArchivageService.class);

    private final ActionDemandeHistoriqueRepository actionDemandeRepository;
    private final ObjectMapper objectMapper;

    public HistoriqueDemandeArchivageService(
            ActionDemandeHistoriqueRepository actionDemandeRepository,
            ObjectMapper objectMapper) {
        this.actionDemandeRepository = actionDemandeRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enregistrerCloture(Long demandeId,
                                   String referenceDemande,
                                   String statutFinal,
                                   Long archiveParUserId,
                                   LocalDateTime dateCloture) {
        persisterCloture(demandeId, referenceDemande, statutFinal, archiveParUserId, dateCloture);
        log.info("Événement clôture enregistré — demande={} statut={}", demandeId, statutFinal);
    }

    private void persisterCloture(Long demandeId,
                                  String referenceDemande,
                                  String statutFinal,
                                  Long archiveParUserId,
                                  LocalDateTime dateCloture) {
        ActionDemandeHistorique cloture = new ActionDemandeHistorique();
        cloture.setDemandeId(demandeId);
        cloture.setReferenceDemande(referenceDemande);
        cloture.setTypeAction(TypeActionDemande.CLOTURE);
        cloture.setLibelle("Demande clôturée et archivée");
        cloture.setStatutAvant(null);
        cloture.setStatutApres(statutFinal);
        cloture.setActeurUserId(archiveParUserId);
        cloture.setActeurRole("BANQUE");
        cloture.setDetailsJson(detailsJson("statutFinal", statutFinal));
        cloture.setDateAction(dateCloture != null ? dateCloture : LocalDateTime.now());
        actionDemandeRepository.save(cloture);
    }

    private String detailsJson(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (key != null && value != null && !value.toString().isBlank()) {
            map.put(key, value);
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
