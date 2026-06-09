package tn.uib.bnpl.gestion_demande.classes;

import java.util.Locale;
import java.util.Set;

/**
 * Statuts du cycle de vie d'une {@link DemandeFinancement} (diagramme d'états BNPL).
 */
public final class StatutDemande {

    public static final String CREE = "CREE";
    public static final String EN_ATTENTE_CONSENTEMENT = "EN_ATTENTE_CONSENTEMENT";
    public static final String EN_COURS_PRESCORING = "EN_COURS_PRESCORING";
    public static final String SOUMISE = "SOUMISE";
    public static final String EN_COURS_ANALYSE = "EN_COURS_ANALYSE";
    public static final String EN_ATTENTE_COMPLEMENT = "EN_ATTENTE_COMPLEMENT";
    public static final String ACCEPTEE = "ACCEPTEE";
    public static final String REFUSEE = "REFUSEE";
    /** Rejet automatique prescoring (alias historique {@code REJET_AUTO}). */
    public static final String REJETEE_AUTO = "REJETEE_AUTO";
    public static final String ANNULEE = "ANNULEE";
    public static final String CLOTUREE = "CLOTUREE";

    private static final Set<String> REJET_AUTO_ALIASES = Set.of(
            REJETEE_AUTO,
            "REJET_AUTO"
    );

    private StatutDemande() {
    }

    public static String upper(String statut) {
        return statut == null ? "" : statut.toUpperCase(Locale.ROOT);
    }

    public static boolean isRejetAutoPrescoring(String statut) {
        return REJET_AUTO_ALIASES.contains(upper(statut));
    }

    public static void exigerStatut(DemandeFinancement demande, String... statutsAutorises) {
        String actuel = upper(demande.getStatut());
        for (String attendu : statutsAutorises) {
            if (upper(attendu).equals(actuel)) {
                return;
            }
        }
        throw new IllegalStateException(
                "Statut invalide pour cette action (actuel=" + demande.getStatut() + ")");
    }
}
