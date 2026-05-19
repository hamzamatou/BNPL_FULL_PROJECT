package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.exceptions.CoherenceAnomalyException;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service IA — Phase pré-création.
 *
 *  1. analyseCoherence()    → POST /coherence/check
 *     Appelé dès que le commerçant joint ses documents (step 3 Angular).
 *     Lance CoherenceAnomalyException si anomalies bloquantes.
 *
 *  2. analyseRecommandation() → GET /recommendation/generate
 *     Appelé uniquement si cohérence OK.
 *     Retourne la liste de recommandations à afficher dans le popup Angular.
 *
 * Ces deux méthodes sont appelées par DemandeController AVANT la création
 * effective de la demande. La création + prescoring n'ont lieu qu'après
 * consentement du client.
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

    // =========================================================================
    // 1. COHÉRENCE OCR
    // =========================================================================

    /**
     * Envoie les données déclarées + fichiers au service Python pour vérification.
     *
     * @param request données du formulaire commerçant
     * @param files   map typeDocument → MultipartFile
     * @return CoherenceResultDto (score, anomalies, corrections)
     * @throws CoherenceAnomalyException si le résultat contient des anomalies bloquantes
     */
    public CoherenceResultDto analyseCoherence(
            CreationDemandeCompleteRequest request,
            Map<String, MultipartFile> files
    ) {
        try {
            // Construire la map declared_data → JSON string
            Map<String, Object> declared = buildDeclaredData(request);
            String declaredJson = objectMapper.writeValueAsString(declared);

            CoherenceResultDto result = scoringClient.checkCoherence(
                    declaredJson,
                    files.get("cin"),
                    files.get("fiche_paie_m1"),
                    files.get("fiche_paie_m2"),
                    files.get("fiche_paie_m3"),
                    files.get("attestation_travail")
            );

            // Documents manquants → bloquant
            if (result.documentsManquants() != null && !result.documentsManquants().isEmpty()) {
                throw new CoherenceAnomalyException(
                        "Documents manquants : " + result.documentsManquants(),
                        List.of("Documents manquants : " + String.join(", ", result.documentsManquants()))
                );
            }

            // Anomalies bloquantes → exception
            if (!result.isConforme()) {
                throw new CoherenceAnomalyException(
                        "Incohérences détectées dans le dossier",
                        result.anomalieMessages()
                );
            }

            return result;

        } catch (CoherenceAnomalyException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erreur service cohérence : {}", e.getMessage(), e);
            throw new RuntimeException("Service de cohérence indisponible", e);
        }
    }

    // =========================================================================
    // 2. RECOMMANDATIONS FINANCIÈRES
    // =========================================================================

    /**
     * Génère les recommandations IA pour le dossier (après cohérence OK).
     *
     * @param request données financières du formulaire
     * @return liste de phrases de recommandation (string[])
     */
    public List<String> analyseRecommandation(CreationDemandeCompleteRequest request) {
        try {
            BigDecimal loyer         = safe(request.getLoyerMensuel());
            BigDecimal mensualites   = safe(request.getMensualitesCredits());
            BigDecimal autresCharges = safe(request.getAutresChargesFixes());
            BigDecimal chargeEnfants = BigDecimal.valueOf(
                    Math.max(0, safeInt(request.getNombreEnfants()))).multiply(BigDecimal.valueOf(300));
            BigDecimal chargesTotal  = loyer.add(mensualites).add(autresCharges).add(chargeEnfants);

            RecommandationResultDto result = scoringClient.generateRecommandation(
                    str(request.getRevenuMensuelNet()),
                    chargesTotal.toPlainString(),
                    str(request.getMensualitesCredits()),
                    str(request.getEncoursCredits()),
                    str(request.getAncienneteEmploiMois()),
                    str(request.getMontant()),
                    str(request.getDureeMois())
            );

            List<String> reco = result.recommandations();
            return (reco != null && !reco.isEmpty()) ? reco : List.of();

        } catch (Exception e) {
            log.warn("Service recommandations indisponible : {}", e.getMessage());
            return List.of(); // non bloquant — le flux continue
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Map<String, Object> buildDeclaredData(CreationDemandeCompleteRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cin",                    req.getCin());
        m.put("revenu_mensuel",         str(req.getRevenuMensuelNet()));
        m.put("loyer_mensuel",          str(req.getLoyerMensuel()));
        m.put("anciennete_emploi_mois", str(req.getAncienneteEmploiMois()));
        m.put("montant",                str(req.getMontant()));
        m.put("aUnLoyer",               req.getLoyerMensuel() != null
                                         && req.getLoyerMensuel().compareTo(BigDecimal.ZERO) > 0);
        return m;
    }

    private static BigDecimal safe(BigDecimal v)   { return v == null ? BigDecimal.ZERO : v; }
    private static int        safeInt(Integer v)   { return v == null ? 0 : v; }
    private static String     str(Object v)        { return v == null ? "0" : v.toString(); }
}