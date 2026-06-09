package tn.uib.bnpl.gestion_demande.camunda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.dto.AnalyseIAResponse;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;
import tn.uib.bnpl.gestion_demande.services.AnalyseIAService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validation IA partagée entre les handlers Camunda et les APIs coherence / recommandations.
 */
@Component
public class WorkflowCoherenceHelper {

    public static final String VAR_COHERENCE_DONE = "coherenceDone";
    public static final String VAR_RECOMMANDATIONS_DONE = "recommandationsDone";
    public static final String VAR_COHERENT = "coherent";
    public static final String VAR_ANOMALIES_JSON = "anomaliesJson";
    public static final String VAR_MESSAGE_COHERENCE = "coherenceMessage";

    private final AnalyseIAService analyseIAService;
    private final ObjectMapper objectMapper;

    public WorkflowCoherenceHelper(AnalyseIAService analyseIAService, ObjectMapper objectMapper) {
        this.analyseIAService = analyseIAService;
        this.objectMapper = objectMapper;
    }

    /** Worker cohérence : POST /coherence/check uniquement. */
    public CoherenceExecutionResult executerValidation(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files) {
        try {
            AnalyseIAService.CoherenceCheckResult coherence =
                    analyseIAService.executerCoherence(request, files);
            Map<String, Object> processVars = new LinkedHashMap<>();
            processVars.put(VAR_COHERENT, true);
            processVars.put(VAR_COHERENCE_DONE, true);
            try {
                processVars.put("correctionsJson",
                        objectMapper.writeValueAsString(
                                coherence.corrections() != null ? coherence.corrections() : Map.of()));
            } catch (Exception ignored) {
                processVars.put("correctionsJson", "{}");
            }
            processVars.put("recommandationsJson", "[]");
            AnalyseIAResponse partial = new AnalyseIAResponse(
                    List.of(),
                    coherence.corrections(),
                    coherence.alertes()
            );
            return CoherenceExecutionResult.ok(partial, processVars);
        } catch (CoherenceAnomalyException ex) {
            Map<String, Object> processVars = new LinkedHashMap<>();
            processVars.put(VAR_COHERENT, false);
            processVars.put(VAR_COHERENCE_DONE, true);
            try {
                processVars.put("correctionsJson", objectMapper.writeValueAsString(ex.getCorrections()));
                processVars.put(VAR_ANOMALIES_JSON, objectMapper.writeValueAsString(ex.getAnomalies()));
            } catch (Exception e) {
                processVars.put("correctionsJson", "{}");
                processVars.put(VAR_ANOMALIES_JSON, "[]");
            }
            processVars.put(VAR_MESSAGE_COHERENCE, ex.getMessage());
            return CoherenceExecutionResult.ko(ex, processVars);
        }
    }

    /** Worker recommandations : GET /recommendation/generate. */
    public List<String> executerRecommandations(CreationDemandeCompleteRequest request) {
        return analyseIAService.executerRecommandations(request);
    }

    public List<String> parseRecommandationsJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    public record CoherenceExecutionResult(
            boolean success,
            AnalyseIAResponse analyseResponse,
            CoherenceAnomalyException anomaly,
            Map<String, Object> processVariables
    ) {
        static CoherenceExecutionResult ok(AnalyseIAResponse response, Map<String, Object> vars) {
            return new CoherenceExecutionResult(true, response, null, vars);
        }

        static CoherenceExecutionResult ko(CoherenceAnomalyException ex, Map<String, Object> vars) {
            return new CoherenceExecutionResult(false, null, ex, vars);
        }
    }
}
