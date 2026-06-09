package tn.uib.bnpl.reporting_archivage.feign.dto;

import java.util.Map;

public record ReferentielKpiFeignDto(
        long clientsInscrits,
        long commercantsPartenaires,
        long banquesPartenaires,
        long utilisateursActifs,
        long utilisateursTotal,
        Map<String, String> commercantsLabels
) {}
