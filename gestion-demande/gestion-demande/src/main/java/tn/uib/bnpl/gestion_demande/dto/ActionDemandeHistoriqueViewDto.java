package tn.uib.bnpl.gestion_demande.dto;

import java.time.LocalDateTime;

/**
 * Vue d'une action demande renvoyée par reporting-archivage.
 */
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
