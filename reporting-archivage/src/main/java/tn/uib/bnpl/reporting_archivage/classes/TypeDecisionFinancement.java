package tn.uib.bnpl.reporting_archivage.classes;

public enum TypeDecisionFinancement {
    /** Décisions bancaires (instruction). Valeurs legacy conservées pour la lecture d'anciennes lignes. */
    ROUTAGE,
    PRISE_EN_CHARGE,
    ACCEPTEE,
    REFUSEE,
    DEMANDE_COMPLEMENTS,
    SCORING_IA,
    AUTRE
}
