package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;
import tn.uib.bnpl.gestion_demande.camunda.PrescoringWorkflowHelper;
import tn.uib.bnpl.gestion_demande.classes.*;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.*;
import tn.uib.bnpl.gestion_demande.client.ReportingArchivageClient;
import tn.uib.bnpl.gestion_demande.repository.*;
import tn.uib.bnpl.gestion_demande.security.SecurityUtils;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service de gestion des demandes — flux corrigé avec IA pré-création.
 *
 * Flux création :
 *  1. POST /analyse (front) — cohérence + reco si OK, sans BDD
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
    private final Optional<CamundaWorkflowService> camundaWorkflowService;
    private final PrescoringWorkflowHelper      prescoringWorkflowHelper;
    private final DemandeHistoriqueService      historiqueService;
    private final PriseEnChargeRepository         priseEnChargeRepository;
    private final DemandeDtoMapper                demandeDtoMapper;
    private final ReportingArchivageClient        reportingArchivageClient;
    private final AnnulationWorkflowHelper        annulationWorkflowHelper;

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
                              ObjectMapper                 objectMapper,
                              Optional<CamundaWorkflowService> camundaWorkflowService,
                              PrescoringWorkflowHelper prescoringWorkflowHelper,
                              DemandeHistoriqueService historiqueService,
                              PriseEnChargeRepository priseEnChargeRepository,
                              DemandeDtoMapper demandeDtoMapper,
                              ReportingArchivageClient reportingArchivageClient,
                              AnnulationWorkflowHelper annulationWorkflowHelper) {
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
        this.camundaWorkflowService = camundaWorkflowService;
        this.prescoringWorkflowHelper = prescoringWorkflowHelper;
        this.historiqueService = historiqueService;
        this.priseEnChargeRepository = priseEnChargeRepository;
        this.demandeDtoMapper = demandeDtoMapper;
        this.reportingArchivageClient = reportingArchivageClient;
        this.annulationWorkflowHelper = annulationWorkflowHelper;
    }

    @Override
    public void archiverEtSupprimer(Long demandeId, String statutFinal, Long archiveParUserId) {
        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        if (!"ACCEPTEE".equalsIgnoreCase(statutFinal) && !"REFUSEE".equalsIgnoreCase(statutFinal)) {
            throw new IllegalStateException(
                    "Archivage autorisé uniquement pour ACCEPTEE/REFUSEE, reçu: " + statutFinal);
        }

        DemandeCompleteResponse complete = demandeDtoMapper.toComplete(demande);
        DemandeCompleteResponse.ClientLiteDto client = complete.client();
        DossierClient dossier = demande.getDossierClient();
        LocalDateTime dateCloture = LocalDateTime.now();

        historiqueService.enregistrer(
                demandeId,
                "CLOTURE",
                "Demande clôturée",
                "Archivage après décision " + statutFinal,
                statutFinal,
                StatutDemande.CLOTUREE,
                archiveParUserId,
                null,
                "SYSTEME",
                dateCloture
        );

        ArchivageDemandeRequest request = new ArchivageDemandeRequest(
                demandeId,
                statutFinal,
                demande.getReferenceDemande(),
                client != null ? client.id() : (dossier != null ? dossier.getClientId() : null),
                client != null ? client.cin() : null,
                demande.getMontant(),
                demande.getDureeMois(),
                demande.getTypeProduit(),
                toJson(complete),
                buildDocumentsMetadataJson(dossier),
                archiveParUserId,
                dateCloture
        );

        reportingArchivageClient.archiverDemande(request);
        log.info("Demande archivée dans reporting-archivage — id={} statut={}", demandeId, statutFinal);

        supprimerDemandeActive(demande);
        log.info("Demande supprimée de gestion-demande — id={}", demandeId);
    }

    private void supprimerDemandeActive(DemandeFinancement demande) {
        Long demandeId = demande.getId();
        priseEnChargeRepository.deleteByDemandeId(demandeId);
        if (demande.getPrescoringScore() != null) {
            prescoringScoreRepo.delete(demande.getPrescoringScore());
        }
        if (demande.getRecommandation() != null) {
            recommandationRepo.delete(demande.getRecommandation());
        }
        demandeRepo.delete(demande);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Snapshot archivage JSON invalide", ex);
        }
    }

    private String buildDocumentsMetadataJson(DossierClient dossier) {
        if (dossier == null || dossier.getDocuments() == null || dossier.getDocuments().isEmpty()) {
            return null;
        }
        List<Map<String, Object>> docs = dossier.getDocuments().stream()
                .map(this::mapDocumentMeta)
                .toList();
        return toJson(docs);
    }

    private Map<String, Object> mapDocumentMeta(DocumentDossier doc) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("id", doc.getId());
        meta.put("typeDocument", doc.getTypeDocument());
        meta.put("objectKey", doc.getObjectKey());
        meta.put("nomFichier", doc.getNomFichier());
        meta.put("contentType", doc.getContentType());
        meta.put("tailleOctets", doc.getTailleOctets());
        return meta;
    }

    // =========================================================================
    // CRÉATION — IA déjà validée via /analyse-ia, recommandations transmises par le front
    // =========================================================================

    @Override
    public DemandeFinancement creerDemandeComplete(
            CreationDemandeCompleteRequest request,
            String recommandationsJson,
            String processInstanceId
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
        if (processInstanceId != null && !processInstanceId.isBlank()) {
            demande.setProcessInstanceId(processInstanceId);
        }
        demande = demandeRepo.save(demande);

        if (processInstanceId != null && !processInstanceId.isBlank()) {
            final String camundaInstanceId = processInstanceId;
            final Long demandeIdFinal = demande.getId();
            camundaWorkflowService.ifPresent(camunda ->
                    camunda.soumettreDemandeCommercant(camundaInstanceId, demandeIdFinal));
        }

        // ── Persistance des recommandations (calculées par le micro IA) ───────
        Recommandation reco = Recommandation.of(demande, recoJson);
        recommandationRepo.save(reco);
        demande.setRecommandation(reco);

        List<String> recommandations = parseRecommandationsList(recoJson);
        historiqueService.enregistrer(
                demande.getId(),
                "RECOMMANDATION",
                "Recommandations IA générées",
                recommandations.size() + " proposition(s) de financement",
                null,
                demande.getStatut(),
                commercantUserId,
                SecurityUtils.getCurrentUserEmail(),
                "COMMERCANT",
                now,
                HistoriqueIaDetails.recommandations(recommandations)
        );

        // ── Upload documents MinIO ────────────────────────────────────────────
        uploadDocuments(request, clientId, dossier);

        // ── Envoi email consentement au client ────────────────────────────────
        actionClientService.requestConsentementEmail(
            demande.getId(), request.getEmail(),
            TypeActionClient.CONSENTEMENT, frontBaseUrl
        );

        demande = demandeRepo.findById(demande.getId()).orElse(demande);
        historiqueService.enregistrer(
                demande.getId(),
                "CREATION",
                "Demande créée",
                "Dossier BNPL enregistré — documents joints",
                null,
                demande.getStatut(),
                commercantUserId,
                SecurityUtils.getCurrentUserEmail(),
                "COMMERCANT",
                now
        );
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

        String avant = demande.getStatut();
        LocalDateTime now = LocalDateTime.now();
        demande.setStatut("SOUMISE");
        demande.setDateDerniereMiseAJour(now);
        demande = demandeRepo.save(demande);
        historiqueService.enregistrer(
                demande.getId(),
                "CONSENTEMENT_VALIDE",
                "Consentement client validé",
                "La demande est soumise pour traitement",
                avant,
                "SOUMISE",
                demande.getDossierClient() != null ? demande.getDossierClient().getClientId() : null,
                null,
                "CLIENT",
                now
        );

        String processInstanceId = demande.getProcessInstanceId();
        if (camundaWorkflowService.isPresent()) {
            if (processInstanceId != null && !processInstanceId.isBlank()) {
                camundaWorkflowService.get().validerConsentementClient(processInstanceId);
            }
        } else {
            prescoringWorkflowHelper.executerPrescoring(demande.getId());
        }

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
    public List<DemandeSummaryResponse> listerDemandesParCommercant(Long commercantId) {
        Long authId = SecurityUtils.getCurrentUserId();
        if (!authId.equals(commercantId)) {
            throw new IllegalStateException("L'id ne correspond pas à l'utilisateur authentifié");
        }
        return demandeRepo.findByCommercantUserId(commercantId).stream()
                .map(this::mapSummaryWithClient)
                .toList();
    }

    @Override
    public List<DemandeSummaryResponse> listerDemandesEnCoursPourAdmin() {
        return demandeRepo.findAllWithDossierForAdmin().stream()
                .map(this::mapSummaryWithClient)
                .toList();
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
    public DemandeFinancement annulerDemande(Long demandeId) {
        DemandeFinancement demande = getDemandeParIdPourCommercant(demandeId);
        if ("ANNULEE".equalsIgnoreCase(demande.getStatut())) {
            return demande;
        }
        annulationWorkflowHelper.verifierAnnulable(demande);

        Long acteurUserId = SecurityUtils.getCurrentUserId();
        String acteurEmail = SecurityUtils.getCurrentUserEmail();
        String acteurRole = "COMMERCANT";

        String instanceId = demande.getProcessInstanceId();
        if (camundaWorkflowService.isPresent() && instanceId != null && !instanceId.isBlank()) {
            camundaWorkflowService.get().declencherAnnulationCommercant(
                    instanceId, demandeId, acteurUserId, acteurEmail, acteurRole);
            return demandeRepo.findById(demandeId)
                    .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + demandeId));
        }

        return annulationWorkflowHelper.appliquerAnnulation(
                demandeId, acteurUserId, acteurEmail, acteurRole);
    }

    @Override
    public DemandeFinancement renvoyerMailConsentement(Long demandeId) {
        DemandeFinancement demande = getDemandeParIdPourCommercant(demandeId);
        if (!"EN_ATTENTE_CONSENTEMENT".equalsIgnoreCase(demande.getStatut())) {
            throw new IllegalStateException("Renvoi autorisé uniquement au statut EN_ATTENTE_CONSENTEMENT");
        }

        DossierClient dossier = demande.getDossierClient();
        if (dossier == null || dossier.getClientId() == null) {
            throw new IllegalStateException("Client introuvable pour cette demande");
        }

        ClientIdentityDto client = clientRemoteService.getClientIdentity(dossier.getClientId());
        if (client == null || client.email() == null || client.email().isBlank()) {
            throw new IllegalStateException("Adresse e-mail client introuvable");
        }

        actionClientService.requestConsentementEmail(
                demande.getId(),
                client.email(),
                TypeActionClient.CONSENTEMENT,
                frontBaseUrl
        );

        historiqueService.enregistrer(
                demande.getId(),
                "RENVOI_CONSENTEMENT",
                "Consentement renvoyé",
                "Nouvel e-mail envoyé au client",
                demande.getStatut(),
                demande.getStatut(),
                SecurityUtils.getCurrentUserId(),
                SecurityUtils.getCurrentUserEmail(),
                "COMMERCANT",
                LocalDateTime.now()
        );

        return demandeRepo.findById(demande.getId()).orElse(demande);
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

    private DemandeSummaryResponse mapSummaryWithClient(DemandeFinancement d) {
        return demandeDtoMapper.toSummary(d);
    }

    private List<String> parseRecommandationsList(String recoJson) {
        if (recoJson == null || recoJson.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(recoJson, new TypeReference<List<String>>() {});
            return list != null ? list : List.of();
        } catch (JsonProcessingException ex) {
            return List.of(recoJson.trim());
        }
    }

}