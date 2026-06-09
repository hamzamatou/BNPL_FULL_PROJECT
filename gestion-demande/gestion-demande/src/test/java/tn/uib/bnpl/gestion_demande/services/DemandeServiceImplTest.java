package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import tn.uib.bnpl.gestion_demande.camunda.PrescoringWorkflowHelper;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.DossierClient;
import tn.uib.bnpl.gestion_demande.classes.Recommandation;
import tn.uib.bnpl.gestion_demande.classes.TypeActionClient;
import tn.uib.bnpl.gestion_demande.config.ScoringFeignClient;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.client.ReportingArchivageClient;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.repository.DocumentDossierRepository;
import tn.uib.bnpl.gestion_demande.repository.DossierClientRepository;
import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;
import tn.uib.bnpl.gestion_demande.repository.PrescoringScoreRepository;
import tn.uib.bnpl.gestion_demande.repository.RecommandationRepository;
import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandeServiceImplTest {

    private static final Long COMMERCANT_ID = 100L;
    private static final Long CLIENT_ID = 200L;

    @Mock private DossierClientRepository dossierRepo;
    @Mock private DemandeFinancementRepository demandeRepo;
    @Mock private DocumentDossierRepository documentRepo;
    @Mock private RecommandationRepository recommandationRepo;
    @Mock private PrescoringScoreRepository prescoringScoreRepo;
    @Mock private ClientRemoteService clientRemoteService;
    @Mock private ActionClientService actionClientService;
    @Mock private ScoringFeignClient scoringClient;
    @Mock private MinioClient minioClient;
    @Mock private PrescoringWorkflowHelper prescoringWorkflowHelper;
    @Mock private DemandeHistoriqueService historiqueService;
    @Mock private PriseEnChargeRepository priseEnChargeRepository;
    @Mock private DemandeDtoMapper demandeDtoMapper;
    @Mock private ReportingArchivageClient reportingArchivageClient;
    @Mock private AnnulationWorkflowHelper annulationWorkflowHelper;

    private DemandeServiceImpl demandeService;

    @BeforeEach
    void setUpSecurityContext() {
        demandeService = new DemandeServiceImpl(
                dossierRepo,
                demandeRepo,
                documentRepo,
                recommandationRepo,
                prescoringScoreRepo,
                clientRemoteService,
                actionClientService,
                scoringClient,
                minioClient,
                new ObjectMapper(),
                Optional.empty(),
                prescoringWorkflowHelper,
                historiqueService,
                priseEnChargeRepository,
                demandeDtoMapper,
                reportingArchivageClient,
                annulationWorkflowHelper);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("id", COMMERCANT_ID)
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        authentication.setAuthenticated(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        ReflectionTestUtils.setField(demandeService, "bucket", "bnpl-documents-test");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void creerDemande_succes() {
        CreationDemandeCompleteRequest request = buildCreationRequest();
        when(clientRemoteService.getClientIdByCin("12345678")).thenReturn(CLIENT_ID);
        when(dossierRepo.save(any(DossierClient.class))).thenAnswer(invocation -> {
            DossierClient dossier = invocation.getArgument(0);
            dossier.setId(1L);
            return dossier;
        });
        when(demandeRepo.save(any(DemandeFinancement.class))).thenAnswer(invocation -> {
            DemandeFinancement demande = invocation.getArgument(0);
            ReflectionTestUtils.setField(demande, "id", 10L);
            return demande;
        });
        when(demandeRepo.findById(10L)).thenAnswer(invocation -> {
            DemandeFinancement demande = new DemandeFinancement();
            ReflectionTestUtils.setField(demande, "id", 10L);
            demande.setStatut("CREE");
            demande.setReferenceDemande("DEM-TEST");
            return Optional.of(demande);
        });

        DemandeFinancement result = demandeService.creerDemandeComplete(
                request, "[\"Réduire la durée\"]", null);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getStatut()).isEqualTo("CREE");

        ArgumentCaptor<DemandeFinancement> demandeCaptor = ArgumentCaptor.forClass(DemandeFinancement.class);
        verify(demandeRepo).save(demandeCaptor.capture());
        assertThat(demandeCaptor.getValue().getStatut()).isEqualTo("CREE");
        assertThat(demandeCaptor.getValue().getCommercantUserId()).isEqualTo(COMMERCANT_ID);
        assertThat(demandeCaptor.getValue().getMontant()).isEqualByComparingTo("3000");

        verify(recommandationRepo).save(any(Recommandation.class));
        verify(actionClientService).requestConsentementEmail(
                eq(10L),
                eq("client@example.com"),
                eq(TypeActionClient.CONSENTEMENT),
                eq("http://localhost:4200"));
        verify(documentRepo, never()).save(any());
    }

    @Test
    void creerDemande_recommandationsVides() {
        CreationDemandeCompleteRequest request = buildCreationRequest();
        when(clientRemoteService.getClientIdByCin("12345678")).thenReturn(CLIENT_ID);
        when(dossierRepo.save(any(DossierClient.class))).thenAnswer(invocation -> {
            DossierClient dossier = invocation.getArgument(0);
            dossier.setId(1L);
            return dossier;
        });
        when(demandeRepo.save(any(DemandeFinancement.class))).thenAnswer(invocation -> {
            DemandeFinancement demande = invocation.getArgument(0);
            ReflectionTestUtils.setField(demande, "id", 11L);
            return demande;
        });
        when(demandeRepo.findById(11L)).thenAnswer(invocation -> {
            DemandeFinancement demande = new DemandeFinancement();
            ReflectionTestUtils.setField(demande, "id", 11L);
            demande.setStatut("CREE");
            return Optional.of(demande);
        });

        demandeService.creerDemandeComplete(request, "  ", null);

        ArgumentCaptor<Recommandation> recoCaptor = ArgumentCaptor.forClass(Recommandation.class);
        verify(recommandationRepo).save(recoCaptor.capture());
        assertThat(recoCaptor.getValue().getRecommandationsJson()).isEqualTo("[]");
    }

    @Test
    void validerConsentement_succes() {
        DemandeFinancement demande = new DemandeFinancement();
        ReflectionTestUtils.setField(demande, "id", 42L);
        demande.setStatut("EN_ATTENTE_CONSENTEMENT");
        demande.setReferenceDemande("DEM-42");

        when(actionClientService.validateTokenForConsent("token-abc")).thenReturn(42L);
        when(demandeRepo.findById(42L)).thenReturn(Optional.of(demande));
        when(demandeRepo.save(any(DemandeFinancement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DemandeFinancement result = demandeService.validerConsentementEtSoumettre("token-abc");

        assertThat(result.getStatut()).isEqualTo("SOUMISE");
        verify(prescoringWorkflowHelper).executerPrescoring(42L);
    }

    @Test
    void validerConsentement_tokenInvalide() {
        when(actionClientService.validateTokenForConsent("bad-token")).thenReturn(999L);
        when(demandeRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> demandeService.validerConsentementEtSoumettre("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");

        verify(prescoringWorkflowHelper, never()).executerPrescoring(any());
    }

    private static CreationDemandeCompleteRequest buildCreationRequest() {
        CreationDemandeCompleteRequest request = new CreationDemandeCompleteRequest();
        request.setNom("Ben");
        request.setPrenom("Ali");
        request.setEmail("client@example.com");
        request.setTelephone("22112233");
        request.setCin("12345678");
        request.setAdresse("Tunis");
        request.setSexe("M");
        request.setProfession("Ingénieur");
        request.setEmployeur("UIB");
        request.setTypeContrat("CDI");
        request.setSituationFamiliale("MARIE");
        request.setNombreEnfants(1);
        request.setAncienneteEmploiMois(36);
        request.setRevenuMensuelNet(new BigDecimal("2200"));
        request.setAutresRevenusMensuels(BigDecimal.ZERO);
        request.setRevenuAnnuel(new BigDecimal("26400"));
        request.setEncoursCredits(BigDecimal.ZERO);
        request.setLoyerMensuel(new BigDecimal("500"));
        request.setMensualitesCredits(new BigDecimal("200"));
        request.setAutresChargesFixes(BigDecimal.ZERO);
        request.setMontant(new BigDecimal("3000"));
        request.setDureeMois(12);
        request.setTypeProduit("AUTO");
        request.setDocuments(null);
        return request;
    }
}
