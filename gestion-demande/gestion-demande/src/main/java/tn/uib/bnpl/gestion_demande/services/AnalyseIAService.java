package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.AnalyseIAResponse;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.DossierValidationResultDto;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyse IA via {@code POST /dossier/validate} — utilisé par {@code POST /analyse-ia} (sans persistance).
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

    /**
     * Valide le dossier via le micro IA. Lance {@link CoherenceAnomalyException} si {@code anomalies[]} non vide.
     *
     * @return recommandations (non vide côté IA uniquement si aucune anomalie)
     */
    public AnalyseIAResponse validerAvantCreation(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Au moins un document est requis pour la validation IA.");
        }

        try {
            String declaredJson = buildDeclaredDataJson(request);

            DossierValidationResultDto result = scoringClient.validateDossier(
                    declaredJson,
                    files.get("cin"),
                    files.get("fiche_paie_m1"),
                    files.get("fiche_paie_m2"),
                    files.get("fiche_paie_m3"),
                    files.get("attestation_travail"),
                    files.get("devis"),
                    files.get("justificatif_loyer")
            );

            if (result.documentsManquants() != null && !result.documentsManquants().isEmpty()) {
                throw new CoherenceAnomalyException(
                        "Documents manquants : " + result.documentsManquants(),
                        List.of("Documents manquants : " + String.join(", ", result.documentsManquants())),
                        Map.of()
                );
            }

            if (!result.hasAucuneAnomalie()) {
                Map<String, Object> corrections = result.corrections() != null
                        ? result.corrections()
                        : Map.of();
                throw new CoherenceAnomalyException(
                        result.message() != null && !result.message().isBlank()
                                ? result.message()
                                : "Incohérences détectées dans le dossier — création annulée",
                        result.anomalieMessages(),
                        corrections
                );
            }

            List<String> recommandations = result.recommandations() != null
                    ? result.recommandations()
                    : List.of();

            Map<String, Object> corrections = result.corrections() != null
                    ? result.corrections()
                    : Map.of();

            return new AnalyseIAResponse(
                    recommandations,
                    corrections,
                    List.of(),
                    result.scoreCoherence()
            );

        } catch (CoherenceAnomalyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur micro IA /dossier/validate : {}", e.getMessage(), e);
            throw new RuntimeException("Service de validation IA indisponible", e);
        }
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

    private static String str(Object v) {
        return v == null ? "0" : v.toString();
    }
}
