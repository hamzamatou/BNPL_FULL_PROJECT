package tn.uib.bnpl.gestion_demande.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnulationWorkflowHelperTest {

    @Mock private DemandeFinancementRepository demandeRepo;
    @Mock private DemandeHistoriqueService historiqueService;

    @InjectMocks
    private AnnulationWorkflowHelper helper;

    @Test
    void appliquerAnnulation_metStatutAnnulee() {
        DemandeFinancement demande = new DemandeFinancement();
        ReflectionTestUtils.setField(demande, "id", 1L);
        demande.setStatut("EN_ATTENTE_CONSENTEMENT");
        demande.setCommercantUserId(10L);
        when(demandeRepo.findById(1L)).thenReturn(Optional.of(demande));
        when(demandeRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DemandeFinancement result = helper.appliquerAnnulation(1L, 10L, "a@b.tn", "COMMERCANT");

        assertThat(result.getStatut()).isEqualTo("ANNULEE");
        verify(historiqueService).enregistrer(
                eq(1L),
                eq("ANNULATION"),
                eq("Demande annulée"),
                eq("Annulation par le commerçant"),
                eq("EN_ATTENTE_CONSENTEMENT"),
                eq("ANNULEE"),
                eq(10L),
                eq("a@b.tn"),
                eq("COMMERCANT"),
                any());
    }

    @Test
    void appliquerAnnulation_refuseApresPriseEnCharge() {
        DemandeFinancement demande = new DemandeFinancement();
        ReflectionTestUtils.setField(demande, "id", 2L);
        demande.setStatut("EN_COURS_ANALYSE");
        when(demandeRepo.findById(2L)).thenReturn(Optional.of(demande));

        assertThatThrownBy(() -> helper.appliquerAnnulation(2L, 1L, null, "COMMERCANT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Annulation interdite");

        verify(demandeRepo, never()).save(any());
    }
}
