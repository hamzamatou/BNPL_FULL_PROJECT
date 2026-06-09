package tn.uib.bnpl.reporting_archivage.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.ActionDemandeHistorique;
import tn.uib.bnpl.reporting_archivage.dto.ActionDemandeHistoriqueViewDto;
import tn.uib.bnpl.reporting_archivage.repository.ActionDemandeHistoriqueRepository;

import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ActionDemandeHistoriqueQueryService {

    private final ActionDemandeHistoriqueRepository actionDemandeRepository;
    private final ObjectMapper objectMapper;

    public ActionDemandeHistoriqueQueryService(
            ActionDemandeHistoriqueRepository actionDemandeRepository,
            ObjectMapper objectMapper) {
        this.actionDemandeRepository = actionDemandeRepository;
        this.objectMapper = objectMapper;
    }

    public List<ActionDemandeHistoriqueViewDto> listerParDemande(Long demandeId) {
        return actionDemandeRepository.findByDemandeIdOrderByDateActionAsc(demandeId).stream()
                .map(this::toViewDto)
                .toList();
    }

    private ActionDemandeHistoriqueViewDto toViewDto(ActionDemandeHistorique action) {
        Map<String, Object> details = parseDetails(action.getDetailsJson());
        String typeSource = stringValue(details.get("typeSource"));
        String detail = stringValue(details.get("detail"));
        return new ActionDemandeHistoriqueViewDto(
                typeSource,
                action.getTypeAction() != null ? action.getTypeAction().name() : null,
                action.getLibelle(),
                detail,
                action.getStatutAvant(),
                action.getStatutApres(),
                action.getDateAction()
        );
    }

    private Map<String, Object> parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(detailsJson, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
