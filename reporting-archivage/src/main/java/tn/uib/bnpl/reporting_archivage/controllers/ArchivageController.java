package tn.uib.bnpl.reporting_archivage.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
import tn.uib.bnpl.reporting_archivage.services.ArchivageService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/archivage")
@PreAuthorize("hasAnyAuthority('ADMIN', 'ANALYSTE_BANCAIRE')")
public class ArchivageController {

    private final ArchivageService archivageService;

    public ArchivageController(ArchivageService archivageService) {
        this.archivageService = archivageService;
    }

    @GetMapping("/dossiers")
    public Page<DossierArchive> lister(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime debut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return archivageService.listerArchives(debut, fin,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateArchivage")));
    }

    @GetMapping("/dossiers/{demandeId}")
    public DossierArchive detail(@PathVariable Long demandeId) {
        return archivageService.getByDemandeId(demandeId);
    }
}
