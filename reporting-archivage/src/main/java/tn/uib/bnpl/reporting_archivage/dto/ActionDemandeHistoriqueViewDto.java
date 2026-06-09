package tn.uib.bnpl.reporting_archivage.dto;

import java.time.LocalDateTime;

public record ActionDemandeHistoriqueViewDto(
        String typeSource,
        String typeAction,
        String libelle,
        String detail,
        String statutAvant,
        String statutApres,
        LocalDateTime dateAction
) {
}
