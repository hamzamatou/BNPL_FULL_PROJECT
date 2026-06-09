package tn.uib.bnpl.gestion_utilisateur.dto;

import java.util.Map;

/** Comptages référentiel pour le dashboard admin. */
public record ReferentielKpiDto(
        long clientsInscrits,
        long commercantsPartenaires,
        long banquesPartenaires,
        long utilisateursActifs,
        long utilisateursTotal,
        Map<String, String> commercantsLabels
) {}
