package tn.uib.bnpl.gestion_demande.services;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ActionDemandeTypeMapperTest {

    @Test
    void routageVaDansActionDemandeUniquement() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("ROUTAGE", "SOUMISE")).isEqualTo("ROUTAGE");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("ROUTAGE", "SOUMISE")).isEmpty();
    }

    @Test
    void prescoringVaDansActionDemandeUniquement() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("PRESCORING", "EN_COURS_PRESCORING")).isEqualTo("SCORING");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("PRESCORING", "EN_COURS_PRESCORING")).isEmpty();
    }

    @Test
    void priseEnChargeVaDansActionDemandeUniquement() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("PRISE_EN_CHARGE", "EN_COURS_ANALYSE")).isEqualTo("PRISE_EN_CHARGE");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("PRISE_EN_CHARGE", "EN_COURS_ANALYSE")).isEmpty();
    }

    @Test
    void decisionBancaireVaDansLesDeuxTables() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("DECISION", "ACCEPTEE")).isEqualTo("ACCEPTION");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("DECISION", "ACCEPTEE"))
                .contains("ACCEPTEE");

        assertThat(ActionDemandeTypeMapper.mapTypeAction("DECISION", "REFUSEE")).isEqualTo("REFUS");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("DECISION", "REFUSEE"))
                .contains("REFUSEE");

        assertThat(ActionDemandeTypeMapper.mapTypeDecision("DECISION", "SOUMISE"))
                .contains("REFUSEE");
    }

    @Test
    void complementsBancaireVaDansLesDeuxTables() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("COMPLEMENTS", "EN_ATTENTE_COMPLEMENT"))
                .isEqualTo("COMPLEMENTS");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("COMPLEMENTS", "EN_ATTENTE_COMPLEMENT"))
                .contains("DEMANDE_COMPLEMENTS");
    }

    @Test
    void recommandationVaDansActionDemandeUniquement() {
        assertThat(ActionDemandeTypeMapper.mapTypeAction("RECOMMANDATION", "CREE")).isEqualTo("RECOMMANDATION");
        assertThat(ActionDemandeTypeMapper.mapTypeDecision("RECOMMANDATION", "CREE")).isEmpty();
    }
}
