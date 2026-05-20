package tn.uib.bnpl.gestion_demande.dto;

import java.util.List;

/** Réponse HTTP 201 de POST /creation-complete. */
public record CreationCompleteResponse(
        DemandeSummaryResponse demande,
        List<String> recommandations
) {}
