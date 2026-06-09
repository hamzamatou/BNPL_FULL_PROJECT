package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.AnalyseIAResponse;
import tn.uib.bnpl.gestion_demande.dto.CoherenceResultDto;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.RecommandationResultDto;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyse IA en deux appels micro Python :
 * 1. {@code POST /coherence/check}
 * 2. {@code GET /recommendation/generate} uniquement si {@code anomalies[]} vide
 */
@Service
public class AnalyseIAService {

    private static final Logger log = LoggerFactory.getLogger(AnalyseIAService.class);

    private final ScoringFeignClient scoringClient;
    private final ObjectMapper       objectMapper;

    public AnalyseIAService(ScoringFeignClient scoringClient, ObjectMapper objectMapper) {
        this.scoringClient = scoringClient;
        this.objectMapper  = objectMapper;
    }

    public record CoherenceCheckResult(
            Map<String, Object> corrections,
            List<String> alertes
    ) {}

    /** Résultat cohérence OK + recommandations (appel micro GET /recommendation/generate). */
    public record CoherenceAvecRecommandationsResult(
            Map<String, Object> corrections,
            List<String> alertes,
            List<String> recommandations
    ) {}

    /**
     * 1) POST /coherence/check — anomalies + corrections uniquement côté micro.
     * 2) Si aucune anomalie bloquante : GET /recommendation/generate.
     */
    public CoherenceAvecRecommandationsResult executerCoherencePuisRecommandations(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        CoherenceCheckResult coherence = executerCoherence(request, files);
        List<String> recommandations = executerRecommandations(request);
        return new CoherenceAvecRecommandationsResult(
                coherence.corrections(),
                coherence.alertes(),
                recommandations != null ? recommandations : List.of()
        );
    }

    /**
     * Étape 1 — cohérence OCR uniquement (pas de recommandations).
     */
    public CoherenceCheckResult executerCoherence(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Au moins un document est requis pour la validation IA.");
        }

        try {
            String declaredJson = buildDeclaredDataJson(request);
            CoherenceResultDto result = scoringClient.checkCoherence(
                    declaredJson,
                    files.get("cin"),
                    files.get("fiche_paie_m1"),
                    files.get("fiche_paie_m2"),
                    files.get("fiche_paie_m3"),
                    files.get("attestation_travail"),
                    files.get("devis"),
                    files.get("justificatif_loyer")
            );

            Map<String, Object> corrections = result.corrections() != null
                    ? result.corrections()
                    : Map.of();

            if (result.documentsManquants() != null && !result.documentsManquants().isEmpty()) {
                throw CoherenceAnomalyException.fromMessages(
                        "Documents manquants : " + result.documentsManquants(),
                        List.of("Documents manquants : " + String.join(", ", result.documentsManquants())),
                        corrections
                );
            }

            if (!result.hasAucuneAnomalie()) {
                String message = result.message() != null && !result.message().isBlank()
                        ? result.message()
                        : "Incohérences détectées dans le dossier — création annulée";
                throw new CoherenceAnomalyException(
                        message,
                        result.anomalies() != null ? result.anomalies() : List.of(),
                        corrections
                );
            }

            return new CoherenceCheckResult(corrections, result.alerteMessages());

        } catch (CoherenceAnomalyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur micro IA /coherence/check : {}", e.getMessage(), e);
            throw new RuntimeException("Service de cohérence IA indisponible", e);
        }
    }

    /**
     * Étape 2 — recommandations (uniquement après cohérence OK).
     */
    public List<String> executerRecommandations(CreationDemandeCompleteRequest request) {
        try {
            BigDecimal revenuNet = safe(request.getRevenuMensuelNet())
                    .add(safe(request.getAutresRevenusMensuels()));
            BigDecimal loyer = safe(request.getLoyerMensuel());
            BigDecimal mensualites = safe(request.getMensualitesCredits());
            BigDecimal autresCharges = safe(request.getAutresChargesFixes());
            int enfants = Math.max(0, safeInt(request.getNombreEnfants()));
            BigDecimal chargesTotal = loyer
                    .add(mensualites)
                    .add(autresCharges)
                    .add(BigDecimal.valueOf(enfants * 300L));

            RecommandationResultDto result = scoringClient.generateRecommandation(
                    str(revenuNet),
                    str(chargesTotal),
                    str(mensualites),
                    str(request.getEncoursCredits()),
                    str(request.getAncienneteEmploiMois()),
                    str(request.getMontant()),
                    str(request.getDureeMois())
            );

            return result.recommandations() != null ? result.recommandations() : List.of();

        } catch (Exception e) {
            log.error("Erreur micro IA /recommendation/generate : {}", e.getMessage(), e);
            throw new RuntimeException("Service de recommandations IA indisponible", e);
        }
    }

    /**
     * Enchaînement cohérence puis recommandations (POST /analyse).
     */
    public AnalyseIAResponse analyserAvantCreation(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        CoherenceCheckResult coherence = executerCoherence(request, files);
        List<String> recommandations = executerRecommandations(request);
        return new AnalyseIAResponse(
                recommandations,
                coherence.corrections(),
                coherence.alertes()
        );
    }

    /** @deprecated utiliser {@link #analyserAvantCreation} */
    @Deprecated
    public AnalyseIAResponse validerAvantCreation(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        return analyserAvantCreation(request, files);
    }

    private String buildDeclaredDataJson(CreationDemandeCompleteRequest req) throws JsonProcessingException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cin", req.getCin());
        m.put("revenu_mensuel", str(req.getRevenuMensuelNet()));
        m.put("revenu_mensuel_net", str(req.getRevenuMensuelNet()));
        m.put("loyer_mensuel", str(req.getLoyerMensuel()));
        m.put("anciennete_emploi_mois", str(req.getAncienneteEmploiMois()));
        m.put("montant", str(req.getMontant()));
        m.put("duree_mois", str(req.getDureeMois()));
        m.put("mensualites_credits", str(req.getMensualitesCredits()));
        m.put("encours_credits", str(req.getEncoursCredits()));
        m.put("autres_charges_fixes", str(req.getAutresChargesFixes()));
        m.put("nombre_enfants", req.getNombreEnfants() != null ? req.getNombreEnfants() : 0);
        m.put("aUnLoyer", req.getLoyerMensuel() != null
                && req.getLoyerMensuel().compareTo(BigDecimal.ZERO) > 0);
        return objectMapper.writeValueAsString(m);
    }

    private static BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static int safeInt(Integer v) {
        return v != null ? v : 0;
    }

    private static String str(Object v) {
        return v == null ? "0" : v.toString();
    }
}
