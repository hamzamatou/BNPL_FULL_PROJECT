package tn.uib.bnpl.reporting_archivage.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
import tn.uib.bnpl.reporting_archivage.dto.ArchivageDemandeRequest;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventPayload;
import tn.uib.bnpl.reporting_archivage.feign.GestionDemandeFeign;
import tn.uib.bnpl.reporting_archivage.repository.DossierArchiveRepository;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class ArchivageServiceImpl implements ArchivageService {

    private static final Logger log = LoggerFactory.getLogger(ArchivageServiceImpl.class);

    private final DossierArchiveRepository dossierArchiveRepository;
    private final GestionDemandeFeign gestionDemandeFeign;
    private final ObjectMapper objectMapper;
    private final HistoriqueDemandeArchivageService historiqueDemandeArchivageService;

    public ArchivageServiceImpl(
            DossierArchiveRepository dossierArchiveRepository,
            GestionDemandeFeign gestionDemandeFeign,
            ObjectMapper objectMapper,
            HistoriqueDemandeArchivageService historiqueDemandeArchivageService) {
        this.dossierArchiveRepository = dossierArchiveRepository;
        this.gestionDemandeFeign = gestionDemandeFeign;
        this.objectMapper = objectMapper;
        this.historiqueDemandeArchivageService = historiqueDemandeArchivageService;
    }

    @Override
    @Transactional
    public DossierArchive archiverDepuisEvenement(AuditEventPayload payload, LocalDateTime dateArchivage,
                                                   String correlationId) {
        if (payload.demandeId() == null) {
            throw new IllegalArgumentException("demandeId obligatoire pour ARCHIVAGE_DOSSIER");
        }
        if (dossierArchiveRepository.existsByDemandeId(payload.demandeId())) {
            return dossierArchiveRepository.findByDemandeId(payload.demandeId()).orElseThrow();
        }

        String snapshot = payload.snapshotJson();
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = fetchSnapshotJson(payload.demandeId());
        }

        DossierArchive archive = new DossierArchive();
        archive.setDemandeId(payload.demandeId());
        archive.setReferenceDemande(payload.referenceDemande());
        archive.setClientId(payload.clientId());
        archive.setCinClient(payload.cinClient());
        archive.setStatutFinal(payload.statutFinal() != null ? payload.statutFinal() : "CLOTURE");
        archive.setMontant(payload.montant());
        archive.setDureeMois(payload.dureeMois());
        archive.setTypeProduit(payload.typeProduit());
        archive.setSnapshotJson(snapshot);
        archive.setDocumentsMetadataJson(payload.documentsMetadataJson());
        archive.setArchiveParUserId(payload.acteurUserId());
        archive.setArchiveParEmail(payload.acteurEmail());
        archive.setDateCloture(payload.dateCloture() != null ? payload.dateCloture() : dateArchivage);
        archive.setDateArchivage(dateArchivage);

        return dossierArchiveRepository.save(archive);
    }

    @Override
    @Transactional
    public DossierArchive archiverDemande(Long demandeId, String statutFinal) {
        if (dossierArchiveRepository.existsByDemandeId(demandeId)) {
            return dossierArchiveRepository.findByDemandeId(demandeId).orElseThrow();
        }
        LocalDateTime now = LocalDateTime.now();
        String snapshot = fetchSnapshotJson(demandeId);
        DossierArchive archive = new DossierArchive();
        archive.setDemandeId(demandeId);
        archive.setStatutFinal(statutFinal);
        archive.setSnapshotJson(snapshot);
        archive.setDateCloture(now);
        archive.setDateArchivage(now);
        enrichirDepuisSnapshot(archive, snapshot);
        if (archive.getReferenceDemande() == null || archive.getReferenceDemande().isBlank()) {
            archive.setReferenceDemande("DEM-" + demandeId);
        }
        return dossierArchiveRepository.save(archive);
    }

    @Override
    @Transactional
    public DossierArchive archiverDemande(ArchivageDemandeRequest request) {
        if (request == null || request.demandeId() == null) {
            throw new IllegalArgumentException("demandeId obligatoire pour archivage");
        }
        if (dossierArchiveRepository.existsByDemandeId(request.demandeId())) {
            DossierArchive existing = dossierArchiveRepository.findByDemandeId(request.demandeId()).orElseThrow();
            historiqueDemandeArchivageService.enregistrerCloture(
                    request.demandeId(),
                    existing.getReferenceDemande(),
                    existing.getStatutFinal(),
                    request.archiveParUserId(),
                    existing.getDateCloture());
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        String snapshot = request.snapshotJson();
        if (snapshot == null || snapshot.isBlank()) {
            snapshot = fetchSnapshotJson(request.demandeId());
        }
        DossierArchive archive = new DossierArchive();
        archive.setDemandeId(request.demandeId());
        archive.setStatutFinal(request.statutFinal() != null ? request.statutFinal() : "CLOTURE");
        archive.setReferenceDemande(request.referenceDemande());
        archive.setClientId(request.clientId());
        archive.setCinClient(request.cinClient());
        archive.setMontant(request.montant());
        archive.setDureeMois(request.dureeMois());
        archive.setTypeProduit(request.typeProduit());
        archive.setSnapshotJson(snapshot);
        archive.setDocumentsMetadataJson(request.documentsMetadataJson());
        archive.setArchiveParUserId(request.archiveParUserId());
        archive.setDateCloture(request.dateCloture() != null ? request.dateCloture() : now);
        archive.setDateArchivage(now);
        enrichirDepuisSnapshot(archive, snapshot);
        if (archive.getReferenceDemande() == null || archive.getReferenceDemande().isBlank()) {
            archive.setReferenceDemande("DEM-" + request.demandeId());
        }
        DossierArchive saved = dossierArchiveRepository.save(archive);
        historiqueDemandeArchivageService.enregistrerCloture(
                request.demandeId(),
                saved.getReferenceDemande(),
                saved.getStatutFinal(),
                request.archiveParUserId(),
                saved.getDateCloture());
        return saved;
    }

    @Override
    public Page<DossierArchive> listerArchives(LocalDateTime debut, LocalDateTime fin, Pageable pageable) {
        if (debut != null && fin != null) {
            return dossierArchiveRepository.findByDateArchivageBetween(debut, fin, pageable);
        }
        return dossierArchiveRepository.findAll(pageable);
    }

    @Override
    public DossierArchive getByDemandeId(Long demandeId) {
        return dossierArchiveRepository.findByDemandeId(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Archive introuvable pour demande " + demandeId));
    }

    private String fetchSnapshotJson(Long demandeId) {
        try {
            Map<String, Object> detail = gestionDemandeFeign.getDemandeDetail(demandeId);
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            log.warn("Impossible de récupérer le détail demande {} via Feign: {}", demandeId, ex.getMessage());
            return "{\"demandeId\":" + demandeId + ",\"source\":\"archivage-sans-detail\"}";
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichirDepuisSnapshot(DossierArchive archive, String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(snapshotJson, Map.class);
            if (archive.getReferenceDemande() == null || archive.getReferenceDemande().isBlank()) {
                Object ref = map.get("referenceDemande");
                if (ref != null) {
                    archive.setReferenceDemande(ref.toString());
                }
            }
            if (archive.getMontant() == null && map.get("montant") instanceof Number n) {
                archive.setMontant(java.math.BigDecimal.valueOf(n.doubleValue()));
            }
            if (archive.getDureeMois() == null && map.get("dureeMois") instanceof Number n) {
                archive.setDureeMois(n.intValue());
            }
            if (archive.getTypeProduit() == null && map.get("typeProduit") != null) {
                archive.setTypeProduit(map.get("typeProduit").toString());
            }
            Object client = map.get("client");
            if (client instanceof Map<?, ?> clientMap) {
                if (archive.getClientId() == null && clientMap.get("id") instanceof Number id) {
                    archive.setClientId(id.longValue());
                }
                if (archive.getCinClient() == null && clientMap.get("cin") != null) {
                    archive.setCinClient(clientMap.get("cin").toString());
                }
            }
        } catch (Exception ex) {
            log.debug("Enrichissement archive depuis snapshot ignoré: {}", ex.getMessage());
        }
    }
}
