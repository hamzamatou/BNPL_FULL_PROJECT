package tn.uib.bnpl.reporting_archivage.controllers;

import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
import tn.uib.bnpl.reporting_archivage.services.ArchivageService;

@RestController
@RequestMapping("/api/internal/archivage")
public class ArchivageInternalController {

    private final ArchivageService archivageService;

    public ArchivageInternalController(ArchivageService archivageService) {
        this.archivageService = archivageService;
    }
    @PostMapping("/dossiers/{demandeId}")
    public DossierArchive archiverDossier(
            @PathVariable Long demandeId,
            @RequestParam(defaultValue = "CLOTURE") String statutFinal) {
        return archivageService.archiverDemande(demandeId, statutFinal);
    }
}
