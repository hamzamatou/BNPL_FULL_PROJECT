package tn.uib.bnpl.reporting_archivage.dto;

import java.time.LocalDateTime;

public record ActionDemandeResumeDto(
        Long id,
        Long demandeId,
        String referenceDemande,
        String typeAction,
        String libelle,
        String acteurEmail,
        LocalDateTime dateAction
) {}
