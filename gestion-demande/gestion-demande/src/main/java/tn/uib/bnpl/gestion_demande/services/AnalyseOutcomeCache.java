package tn.uib.bnpl.gestion_demande.services;

import org.springframework.stereotype.Component;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session courte après cohérence OK (sans Camunda) : enchaînement POST /recommandations.
 */
@Component
public class AnalyseOutcomeCache {

    public record CachedAnalyseSession(
            CreationDemandeCompleteRequest request,
            Map<String, Object> corrections,
            List<String> alertes
    ) {}

    private final Map<String, CachedAnalyseSession> sessions = new ConcurrentHashMap<>();

    public String putCoherenceOk(CachedAnalyseSession session) {
        String id = UUID.randomUUID().toString();
        sessions.put(id, session);
        return id;
    }

    public CachedAnalyseSession getAndRemove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessions.remove(sessionId);
    }
}
