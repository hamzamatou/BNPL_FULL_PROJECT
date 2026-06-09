package tn.uib.bnpl.gestion_demande.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DossierRoutageRequest(
        @JsonProperty("revenu_mensuel_net") double revenuMensuelNet,
        @JsonProperty("charges_mensuelles_totales") double chargesMensuellesTotales,
        @JsonProperty("montant_demande") double montantDemande,
        @JsonProperty("duree_mois") int dureeMois,
        @JsonProperty("anciennete_emploi_mois") int ancienneteEmploiMois,
        @JsonProperty("type_contrat") String typeContrat,
        @JsonProperty("nb_incidents_paiement") int nbIncidentsPaiement,
        @JsonProperty("score_centrale_risque") double scoreCentraleRisque
) {
}
