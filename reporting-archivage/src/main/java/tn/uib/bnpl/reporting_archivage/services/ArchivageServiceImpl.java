package tn.uib.bnpl.reporting_archivage.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
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

    public ArchivageServiceImpl(
            DossierArchiveRepository dossierArchiveRepository,
            GestionDemandeFeign gestionDemandeFeign,
            ObjectMapper objectMapper) {
        this.dossierArchiveRepository = dossierArchiveRepository;
        this.gestionDemandeFeign = gestionDemandeFeign;
        this.objectMapper = objectMapper;
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
            throw new IllegalStateException("Dossier déjà archivé: " + demandeId);
        }
        LocalDateTime now = LocalDateTime.now();
        DossierArchive archive = new DossierArchive();
        archive.setDemandeId(demandeId);
        archive.setStatutFinal(statutFinal);
        archive.setSnapshotJson(fetchSnapshotJson(demandeId));
        archive.setDateCloture(now);
        archive.setDateArchivage(now);
        return dossierArchiveRepository.save(archive);
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
}
