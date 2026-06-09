package tn.uib.bnpl.gestion_demande.services;

import java.util.Locale;
import java.util.Optional;

/**
 * Mappe les événements gestion-demande vers reporting-archivage :
 * <ul>
 *   <li>{@code action_demande_historique} — cycle dossier (création, scoring, routage, prise en charge…)</li>
 *   <li>{@code decision_financement_historique} — décisions bancaires uniquement (accepter, refuser, compléments)</li>
 * </ul>
 */
final class ActionDemandeTypeMapper {

    private ActionDemandeTypeMapper() {
    }

    static String mapTypeAction(String type, String statutApres) {
        String normalized = upper(type);
        return switch (normalized) {
            case "CREATION" -> "CREATION";
            case "CONSENTEMENT_ENVOYE", "CONSENTEMENT_VALIDE", "RENVOI_CONSENTEMENT" -> "CONSENTEMENT";
            case "PRESCORING", "REJET_AUTO", "REJETEE_AUTO" -> "SCORING";
            case "RECOMMANDATION" -> "RECOMMANDATION";
            case "ROUTAGE" -> "ROUTAGE";
            case "PRISE_EN_CHARGE", "ANALYSE", "INSTRUCTION" -> "PRISE_EN_CHARGE";
            case "COMPLEMENTS", "COMPLEMENTS_RECUS" -> "COMPLEMENTS";
            case "CLOTURE" -> "CLOTURE";
            case "DECISION" -> mapDecisionAction(statutApres);
            default -> "AUTRE";
        };
    }

    /**
     * Seules les décisions bancaires sont persistées dans {@code decision_financement_historique}.
     */
    static Optional<String> mapTypeDecision(String type, String statutApres) {
        String normalized = upper(type);
        return switch (normalized) {
            case "COMPLEMENTS" -> Optional.of("DEMANDE_COMPLEMENTS");
            case "DECISION" -> Optional.of(mapDecisionFinancement(statutApres));
            default -> Optional.empty();
        };
    }

    private static String mapDecisionAction(String statutApres) {
        if (statutApres == null) {
            return "AUTRE";
        }
        return switch (upper(statutApres)) {
            case "ACCEPTEE" -> "ACCEPTION";
            case "REFUSEE", "SOUMISE" -> "REFUS";
            default -> "AUTRE";
        };
    }

    private static String mapDecisionFinancement(String statutApres) {
        if (statutApres == null) {
            return "AUTRE";
        }
        return switch (upper(statutApres)) {
            case "ACCEPTEE" -> "ACCEPTEE";
            case "REFUSEE", "SOUMISE" -> "REFUSEE";
            default -> "AUTRE";
        };
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }
}
