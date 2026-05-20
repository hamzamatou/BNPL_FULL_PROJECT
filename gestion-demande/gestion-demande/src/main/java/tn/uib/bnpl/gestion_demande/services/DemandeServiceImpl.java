package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.classes.*;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.repository.*;
import tn.uib.bnpl.gestion_demande.security.SecurityUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service de gestion des demandes — flux corrigé avec IA pré-création.
 *
 * Flux création :
 *  1. POST /analyse-ia (côté front) — cohérence + recommandations, sans BDD
 *  2. POST /creation-complete — persistance + email (reco déjà calculées)
 *  3. Client → POST /consentement/confirm → prescoring
 */
@Service
@Transactional
public class DemandeServiceImpl implements DemandeService {

    private static final Logger log = LoggerFactory.getLogger(DemandeServiceImpl.class);

    private final DossierClientRepository       dossierRepo;
    private final DemandeFinancementRepository  demandeRepo;
    private final DocumentDossierRepository     documentRepo;
    private final RecommandationRepository      recommandationRepo;
    private final PrescoringScoreRepository     prescoringScoreRepo;
    private final ClientRemoteService           clientRemoteService;
    private final ActionClientService           actionClientService;
    private final ScoringFeignClient            scoringClient;
    private final MinioClient                   minioClient;
    private final ObjectMapper                  objectMapper;

    @Value("${minio.bucket:bnpl-documents}")
    private String bucket;

    private final String frontBaseUrl = "http://localhost:4200";

    public DemandeServiceImpl(DossierClientRepository      dossierRepo,
                              DemandeFinancementRepository demandeRepo,
                              DocumentDossierRepository    documentRepo,
                              RecommandationRepository     recommandationRepo,
                              PrescoringScoreRepository    prescoringScoreRepo,
                              ClientRemoteService          clientRemoteService,
                              ActionClientService          actionClientService,
                              ScoringFeignClient           scoringClient,
                              MinioClient                  minioClient,
                              ObjectMapper                 objectMapper) {
        this.dossierRepo         = dossierRepo;
        this.demandeRepo         = demandeRepo;
        this.documentRepo        = documentRepo;
        this.recommandationRepo  = recommandationRepo;
        this.prescoringScoreRepo = prescoringScoreRepo;
        this.clientRemoteService = clientRemoteService;
        this.actionClientService = actionClientService;
        this.scoringClient       = scoringClient;
        this.minioClient         = minioClient;
        this.objectMapper        = objectMapper;
    }

    // =========================================================================
    // CRÉATION — IA déjà validée via /analyse-ia, recommandations transmises par le front
    // =========================================================================

    @Override
    public DemandeFinancement creerDemandeComplete(
            CreationDemandeCompleteRequest request,
            String recommandationsJson
    ) {
        String recoJson = (recommandationsJson != null && !recommandationsJson.isBlank())
                ? recommandationsJson
                : "[]";

        Long commercantUserId = SecurityUtils.getCurrentUserId();
        Long clientId         = resolveOrCreateClient(request);

        // ── Calculs financiers ────────────────────────────────────────────────
        BigDecimal revenus       = safe(request.getRevenuMensuelNet())
                                     .add(safe(request.getAutresRevenusMensuels()));
        BigDecimal loyer         = safe(request.getLoyerMensuel());
        BigDecimal mensualites   = safe(request.getMensualitesCredits());
        BigDecimal autresCharges = safe(request.getAutresChargesFixes());
        BigDecimal chargeEnfants = BigDecimal.valueOf(
                                       Math.max(0, safeInt(request.getNombreEnfants())))
                                   .multiply(BigDecimal.valueOf(300));
        BigDecimal chargesTotal  = loyer.add(mensualites).add(autresCharges).add(chargeEnfants);

        int        dureeMois     = Math.max(1, safeInt(request.getDureeMois()));
        BigDecimal montant       = safe(request.getMontant());
        BigDecimal mensualiteBnpl = montant.divide(BigDecimal.valueOf(dureeMois), 4, RoundingMode.HALF_UP);
        BigDecimal tauxEndettement = revenus.compareTo(BigDecimal.ZERO) > 0
            ? mensualites.add(mensualiteBnpl).divide(revenus, 4, RoundingMode.HALF_UP)
            : null;

        LocalDateTime now = LocalDateTime.now();

        // ── Dossier client ────────────────────────────────────────────────────
        DossierClient dossier = new DossierClient(
            clientId, genererRef("DOS"), now, now,
            request.getAncienneteEmploiMois(),
            request.getTypeContrat(),
            request.getRevenuMensuelNet(),
            request.getAutresRevenusMensuels(),
            safe(request.getRevenuAnnuel()),
            request.getEncoursCredits(),
            loyer, request.getMensualitesCredits(),
            request.getAutresChargesFixes(),
            chargesTotal, tauxEndettement,
            request.getSituationFamiliale(),
            request.getNombreEnfants()
        );
        dossier = dossierRepo.save(dossier);

        // ── Demande (statut CREE → EN_ATTENTE_CONSENTEMENT après envoi mail) ──
        DemandeFinancement demande = new DemandeFinancement(
            dossier, commercantUserId, genererRef("DEM"),
            montant, request.getDureeMois(),
            "CREE", now, now, request.getTypeProduit()
        );
        demande = demandeRepo.save(demande);

        // ── Persistance des recommandations (calculées par le micro IA) ───────
        Recommandation reco = Recommandation.of(demande, recoJson);
        recommandationRepo.save(reco);
        demande.setRecommandation(reco);

        // ── Upload documents MinIO ────────────────────────────────────────────
        uploadDocuments(request, clientId, dossier);

        // ── Envoi email consentement au client ────────────────────────────────
        actionClientService.createActionLink(
            demande.getId(), request.getEmail(),
            TypeActionClient.CONSENTEMENT, frontBaseUrl
        );

        demande = demandeRepo.findById(demande.getId()).orElse(demande);
        log.info("Demande créée — ref={} statut={}", demande.getReferenceDemande(), demande.getStatut());
        return demande;
    }

    // =========================================================================
    // CONSENTEMENT → SOUMISE → prescoring
    // =========================================================================

    @Override
    public DemandeFinancement validerConsentementEtSoumettre(String token) {
        Long demandeId = actionClientService.validateTokenForConsent(token);

        DemandeFinancement demande = demandeRepo.findById(demandeId)
            .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));

        demande.setStatut("SOUMISE");
        demande.setDateDerniereMiseAJour(LocalDateTime.now());
        demande = demandeRepo.save(demande);

        lancerPrescoring(demande);

        log.info("Demande soumise après consentement — ref={}", demande.getReferenceDemande());
        return demande;
    }

    // =========================================================================
    // Autres méthodes (inchangées)
    // =========================================================================

    @Override
    public List<DemandeFinancement> listerDemandesParClient(Long clientId) {
        Long authId = SecurityUtils.getCurrentUserId();
        if (!authId.equals(clientId))
            throw new IllegalStateException("L'id ne correspond pas à l'utilisateur authentifié");
        return demandeRepo.findByCommercantUserId(clientId);
    }

    @Override
    public DemandeFinancement getDemandeParIdPourCommercant(Long demandeId) {
        Long authId = SecurityUtils.getCurrentUserId();
        DemandeFinancement demande = demandeRepo.findCompleteById(demandeId)
            .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));
        if (!authId.equals(demande.getCommercantUserId()))
            throw new IllegalStateException("Cette demande n'appartient pas au commerçant authentifié");
        return demande;
    }

    @Override
    public DernierDossierFinancierResponse getDernierDossierFinancierParCin(String cin) {
        Long clientId = clientRemoteService.getClientIdByCin(cin);
        var dossier = dossierRepo
            .findTopByClientIdOrderByDateDerniereMiseAJourDesc(clientId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Aucun dossier financier trouvé pour CIN=" + cin));
        return new DernierDossierFinancierResponse(
            dossier.getAncienneteEmploiMois() != null ? dossier.getAncienneteEmploiMois() : 0,
            safe(dossier.getRevenuMensuelNet()),
            safe(dossier.getAutresRevenusMensuels()),
            safe(dossier.getLoyerMensuel()),
            safe(dossier.getMensualitesCredits()),
            safe(dossier.getAutresChargesFixes()),
            safe(dossier.getEncoursCredits())
        );
    }

    @Override
    public String getPresignedDocumentUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket).object(objectKey)
                    .method(Method.GET).expiry(10, TimeUnit.MINUTES).build());
        } catch (Exception e) {
            throw new RuntimeException("Impossible de générer l'URL MinIO", e);
        }
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    private void lancerPrescoring(DemandeFinancement demande) {
        DossierClient d = demande.getDossierClient();
        try {
            PrescoringResultDto dto = scoringClient.prescore(
                str(d.getRevenuMensuelNet()),
                str(d.getRevenuAnnuel()),
                str(d.getChargesMensuelles()),
                str(demande.getMontant()),
                str(demande.getDureeMois()),
                str(d.getAncienneteEmploiMois()),
                d.getTypeContrat()
            );
            String explicationsJson = objectMapper.writeValueAsString(
                dto.explications() != null ? dto.explications() : List.of());
            String zoneCode = dto.zone() != null ? dto.zone().code() : "inconnu";

            PrescoringScore score = PrescoringScore.of(demande, dto.pdPct(), dto.score(), zoneCode, explicationsJson);
            prescoringScoreRepo.save(score);
            demande.setPrescoringScore(score);

            log.info("Prescoring OK — demande={} score={} zone={}", demande.getId(), dto.score(), zoneCode);
        } catch (Exception ex) {
            log.error("Prescoring échoué — demande={} : {}", demande.getId(), ex.getMessage());
        }
    }

    private Long resolveOrCreateClient(CreationDemandeCompleteRequest request) {
        try {
            Long clientId = clientRemoteService.getClientIdByCin(request.getCin());
            clientRemoteService.modifierClient(
                clientId, request.getNom(), request.getPrenom(), request.getEmail(),
                request.getTelephone(), request.getCin(), request.getAdresse(),
                request.getSexe(), request.getProfession(), request.getEmployeur(),
                request.getDateNaissance());
            return clientId;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Client introuvable pour CIN")) {
                return clientRemoteService.creerClient(
                    request.getNom(), request.getPrenom(), request.getEmail(),
                    request.getTelephone(), request.getCin(), request.getAdresse(),
                    request.getSexe(), request.getProfession(), request.getEmployeur(),
                    request.getDateNaissance());
            }
            throw ex;
        }
    }

    private void uploadDocuments(CreationDemandeCompleteRequest request, Long clientId, DossierClient dossier) {
        if (request.getDocuments() == null) return;
        for (var docReq : request.getDocuments()) {
            MultipartFile file = docReq.getFile();
            if (file == null || file.isEmpty())
                throw new IllegalArgumentException("Fichier manquant pour : " + docReq.getTypeDocument());
            String filename    = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String contentType = file.getContentType()     != null ? file.getContentType()     : "application/octet-stream";
            String objectKey   = "clients/" + clientId + "/dossiers/" + dossier.getId()
                               + "/" + System.currentTimeMillis() + "-" + filename;
            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectKey)
                    .stream(is, file.getSize(), -1).contentType(contentType).build());
            } catch (Exception e) {
                throw new RuntimeException("Upload MinIO échoué pour " + filename, e);
            }
            documentRepo.save(new DocumentDossier(
                dossier, docReq.getTypeDocument(), objectKey, filename, contentType, file.getSize()));
        }
    }

    private static BigDecimal safe(BigDecimal v)   { return v == null ? BigDecimal.ZERO : v; }
    private static int        safeInt(Integer v)   { return v == null ? 0 : v; }
    private static String     str(Object v)        { return v == null ? null : v.toString(); }
    private static String     genererRef(String p) { return p + "-" + System.currentTimeMillis(); }

}