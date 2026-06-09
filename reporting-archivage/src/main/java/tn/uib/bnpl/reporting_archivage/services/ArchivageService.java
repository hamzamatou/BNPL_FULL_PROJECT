package tn.uib.bnpl.reporting_archivage.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
import tn.uib.bnpl.reporting_archivage.dto.ArchivageDemandeRequest;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventPayload;

import java.time.LocalDateTime;

public interface ArchivageService {

    DossierArchive archiverDepuisEvenement(AuditEventPayload payload, LocalDateTime dateArchivage, String correlationId);

    DossierArchive archiverDemande(Long demandeId, String statutFinal);

    DossierArchive archiverDemande(ArchivageDemandeRequest request);

    Page<DossierArchive> listerArchives(LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    DossierArchive getByDemandeId(Long demandeId);
}
