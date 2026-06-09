package tn.uib.bnpl.gestion_demande.services;



import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tn.uib.bnpl.gestion_demande.camunda.CamundaWorkflowService;

import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.DossierClient;
import tn.uib.bnpl.gestion_demande.classes.PriseEnCharge;
import tn.uib.bnpl.gestion_demande.classes.StatutDemande;

import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import tn.uib.bnpl.gestion_demande.repository.PriseEnChargeRepository;

import tn.uib.bnpl.gestion_demande.security.SecurityUtils;

import tn.uib.bnpl.gestion_demande.dto.ClientIdentityDto;
import tn.uib.bnpl.gestion_demande.dto.DemandeSummaryResponse;

import tn.uib.bnpl.gestion_demande.web.DemandeDtoMapper;



import java.time.LocalDateTime;

import java.util.List;

import java.util.Optional;



@Service

@Transactional

public class PriseEnChargeServiceImpl implements PriseEnChargeService {

    private static final Logger log = LoggerFactory.getLogger(PriseEnChargeServiceImpl.class);

    private final PriseEnChargeRepository priseEnChargeRepository;

    private final DemandeFinancementRepository demandeRepo;

    private final DemandeHistoriqueService historiqueService;

    private final DemandeDtoMapper demandeDtoMapper;

    private final ClientRemoteService clientRemoteService;

    private final NotificationPublisher notificationPublisher;

    private final Optional<CamundaWorkflowService> camundaWorkflowService;



    public PriseEnChargeServiceImpl(

            PriseEnChargeRepository priseEnChargeRepository,

            DemandeFinancementRepository demandeRepo,

            DemandeHistoriqueService historiqueService,

            DemandeDtoMapper demandeDtoMapper,

            ClientRemoteService clientRemoteService,

            NotificationPublisher notificationPublisher,

            @Autowired(required = false) CamundaWorkflowService camundaWorkflowService) {

        this.priseEnChargeRepository = priseEnChargeRepository;

        this.demandeRepo = demandeRepo;

        this.historiqueService = historiqueService;

        this.demandeDtoMapper = demandeDtoMapper;

        this.clientRemoteService = clientRemoteService;

        this.notificationPublisher = notificationPublisher;

        this.camundaWorkflowService = Optional.ofNullable(camundaWorkflowService);

    }



    @Override

    public List<DemandeFinancement> listerDemandesDisponiblesPourBanque() {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();

        return priseEnChargeRepository.findDemandesBanqueNonVerrouillees(banqueUserId, now);

    }



    @Override

    public List<DemandeSummaryResponse> listerDemandesDisponiblesResumePourBanque() {

        return listerDemandesDisponiblesPourBanque().stream()

                .map(demandeDtoMapper::toSummary)

                .toList();

    }



    @Override

    public List<DemandeFinancement> listerDemandesVerrouilleesPourBanque() {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        return priseEnChargeRepository.findDemandesAffecteesVerrouilleesPourBanque(

                banqueUserId, LocalDateTime.now());

    }



    @Override

    public List<DemandeSummaryResponse> listerDemandesVerrouilleesResumePourBanque() {

        return listerDemandesVerrouilleesPourBanque().stream()

                .map(demandeDtoMapper::toSummary)

                .toList();

    }



    @Override

    public DemandeFinancement getDemandeDetailPourBanqueVerrouillee(Long demandeId) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        priseEnChargeRepository.findActiveVerrouillage(demandeId, banqueUserId, now)

                .orElseThrow(() -> new IllegalStateException(

                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));



        return demandeRepo.findCompleteById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

    }



    @Override

    public DemandeFinancement getRecapDemandePourBanque(Long demandeId) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        DemandeFinancement demande = demandeRepo.findCompleteById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));



        boolean verrouillee = priseEnChargeRepository

                .findActiveVerrouillage(demandeId, banqueUserId, now)

                .isPresent();

        if (verrouillee) {

            return demande;

        }



        boolean disponible = priseEnChargeRepository

                .findDemandesBanqueNonVerrouillees(banqueUserId, now)

                .stream()

                .anyMatch(d -> demandeId.equals(d.getId()));

        if (!disponible) {

            throw new IllegalStateException(

                    "Demande non accessible pour cet analyste (non routée ou déjà prise en charge)");

        }



        return demande;

    }



    @Override

    public PriseEnCharge seSaisir(Long demandeId) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        DemandeFinancement demande = demandeRepo.findById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));



        if (!StatutDemande.SOUMISE.equalsIgnoreCase(demande.getStatut())) {

            throw new IllegalStateException("Demande non SOUMISE (statut=" + demande.getStatut() + ")");

        }



        long activeLocks = priseEnChargeRepository.countActiveVerrouillages(demandeId, now);

        if (activeLocks > 0) {

            throw new IllegalStateException("Demande déjà verrouillée (non expirée)");

        }



        PriseEnCharge pec = priseEnChargeRepository

                .findByDemandeIdAndBanqueUserIdAndStatut(demandeId, banqueUserId, "ROUTE")

                .orElseThrow(() -> new IllegalStateException(

                        "Prise en charge en ROUTE introuvable pour cette banque"));



        LocalDateTime expiration = now.plusHours(48);



        pec.setStatut("VERROUILLEE");

        pec.setDateVerrouillage(now);

        pec.setDateExpiration(expiration);

        pec.setDecision(null);

        pec.setDateDebutTraitement(now);

        pec.setDateDecision(null);

        pec.setMotifRefus(null);

        pec.setCommentaire(null);



        PriseEnCharge saved = priseEnChargeRepository.save(pec);

        String statutAvantPrise = demande.getStatut();
        demande.setStatut(StatutDemande.EN_COURS_ANALYSE);
        demande.setDateDerniereMiseAJour(now);
        demandeRepo.save(demande);

        historiqueService.enregistrer(

                demande.getId(),

                "PRISE_EN_CHARGE",

                "Demande prise en charge",

                "Analyse bancaire engagée — fenêtre 48 h pour instruire",

                statutAvantPrise,

                StatutDemande.EN_COURS_ANALYSE,

                banqueUserId,
                null,
                "BANQUE",

                now

        );



        completeCamundaPriseEnCharge(demande, expiration, banqueUserId);

        return saved;

    }



    @Override

    public PriseEnCharge demarrerAnalyse(Long demandeId) {
        return releverInstructionMetier(demandeId, true);
    }



    @Override

    @Deprecated

    public PriseEnCharge seSaisirEtDemarrerAnalyse(Long demandeId) {

        PriseEnCharge pec = seSaisir(demandeId);

        return demarrerAnalyse(demandeId);

    }



    @Override

    public DemandeFinancement accepterDemande(Long demandeId, String commentaire) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        PriseEnCharge pec = priseEnChargeRepository

                .findActiveVerrouillage(demandeId, banqueUserId, now)

                .orElseThrow(() -> new IllegalStateException(

                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));

        DemandeFinancement demande = demandeRepo.findById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        StatutDemande.exigerStatut(demande, StatutDemande.EN_COURS_ANALYSE);

        releverInstructionSiBesoin(pec, demande, false);

        pec.setDecision("ACCEPTEE");

        pec.setCommentaire(commentaire);

        pec.setDateDecision(now);



        pec.setStatut("DEVERROUILLEE");

        pec.setDateVerrouillage(null);

        pec.setDateExpiration(null);

        priseEnChargeRepository.save(pec);



        String avantA = demande.getStatut();

        demande.setStatut(StatutDemande.ACCEPTEE);

        demande.setDateDerniereMiseAJour(now);

        demandeRepo.save(demande);

        historiqueService.enregistrer(

                demande.getId(),

                "DECISION",

                "Demande acceptée",

                commentaire != null && !commentaire.isBlank() ? commentaire : "Financement accordé",

                avantA,

                StatutDemande.ACCEPTEE,

                banqueUserId,
                null,
                "BANQUE",

                now

        );



        ClientIdentityDto client = fetchClient(demande);
        notificationPublisher.publishDecisionAcceptee(demande, client, resolveNomBanqueCourant());

        completeCamundaInstruction(demande, "ACCEPTER", false, commentaire, null, banqueUserId);

        return demande;

    }



    @Override

    public DemandeFinancement refuserDemande(Long demandeId, String motifRefus, String commentaire) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        PriseEnCharge pec = priseEnChargeRepository

                .findActiveVerrouillage(demandeId, banqueUserId, now)

                .orElseThrow(() -> new IllegalStateException(

                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));



        DemandeFinancement demande = demandeRepo.findById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        StatutDemande.exigerStatut(demande, StatutDemande.EN_COURS_ANALYSE);

        releverInstructionSiBesoin(pec, demande, false);

        pec.setDecision("REFUSEE");

        pec.setMotifRefus(motifRefus);

        pec.setCommentaire(commentaire);

        pec.setDateDecision(now);



        pec.setStatut("DEVERROUILLEE");

        pec.setDateVerrouillage(null);

        pec.setDateExpiration(null);

        priseEnChargeRepository.save(pec);



        String avantR = demande.getStatut();

        long remainingRoute = priseEnChargeRepository.countRouteBanksNotRefused(demandeId);

        boolean refusPartiel = remainingRoute > 0;

        String apres;

        if (remainingRoute == 0) {

            demande.setStatut(StatutDemande.REFUSEE);

            apres = StatutDemande.REFUSEE;

        } else {

            demande.setStatut(StatutDemande.SOUMISE);

            apres = StatutDemande.SOUMISE;

        }

        demande.setDateDerniereMiseAJour(now);

        demandeRepo.save(demande);

        String detailRefus = motifRefus != null && !motifRefus.isBlank() ? motifRefus : "Décision défavorable";

        historiqueService.enregistrer(

                demande.getId(),

                "DECISION",

                "REFUSEE".equals(apres) ? "Demande refusée" : "Refus partiel — demande resoumise",

                detailRefus,

                avantR,

                apres,

                banqueUserId,
                null,
                "BANQUE",

                now

        );



        ClientIdentityDto client = fetchClient(demande);
        if (refusPartiel) {
            notificationPublisher.publishDecisionRefuseePartielle(
                    demande, client, motifRefus, resolveNomBanqueCourant());
        } else {
            notificationPublisher.publishDecisionRefusee(
                    demande, client, motifRefus, resolveNomBanqueCourant());
        }

        completeCamundaInstruction(demande, "REFUSER", refusPartiel, commentaire, motifRefus, banqueUserId);

        return demande;

    }



    @Override

    public DemandeFinancement demanderComplements(Long demandeId, String commentaire) {

        Long banqueUserId = SecurityUtils.getCurrentUserId();

        LocalDateTime now = LocalDateTime.now();



        PriseEnCharge pec = priseEnChargeRepository

                .findActiveVerrouillage(demandeId, banqueUserId, now)

                .orElseThrow(() -> new IllegalStateException(

                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));



        DemandeFinancement demande = demandeRepo.findById(demandeId)

                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        StatutDemande.exigerStatut(demande, StatutDemande.EN_COURS_ANALYSE);

        releverInstructionSiBesoin(pec, demande, false);

        pec.setCommentaire(commentaire);

        pec.setDateDebutTraitement(now);

        pec.setDateDecision(null);

        priseEnChargeRepository.save(pec);

        String avantC = demande.getStatut();

        demande.setStatut(StatutDemande.EN_ATTENTE_COMPLEMENT);

        demande.setDateDerniereMiseAJour(now);

        DemandeFinancement saved = demandeRepo.save(demande);

        historiqueService.enregistrer(

                demande.getId(),

                "COMPLEMENTS",

                "Compléments demandés",

                commentaire != null && !commentaire.isBlank() ? commentaire : "Informations complémentaires requises",

                avantC,

                StatutDemande.EN_ATTENTE_COMPLEMENT,

                banqueUserId,
                null,
                "BANQUE",

                now

        );



        ClientIdentityDto client = fetchClient(saved);
        notificationPublisher.publishDemandeComplements(saved, client, commentaire, resolveNomBanqueCourant());

        completeCamundaInstruction(saved, "COMPLEMENT", false, commentaire, null, banqueUserId);

        return saved;

    }

    @Override
    public DemandeFinancement receptionnerComplements(Long demandeId, String detail) {
        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));
        StatutDemande.exigerStatut(demande, StatutDemande.EN_ATTENTE_COMPLEMENT);

        LocalDateTime now = LocalDateTime.now();
        String avant = demande.getStatut();
        demande.setStatut(StatutDemande.EN_COURS_ANALYSE);
        demande.setDateDerniereMiseAJour(now);
        DemandeFinancement saved = demandeRepo.save(demande);

        historiqueService.enregistrer(
                demande.getId(),
                "COMPLEMENTS_RECUS",
                "Informations complémentaires reçues",
                detail != null && !detail.isBlank() ? detail : "Compléments transmis par le client",
                avant,
                StatutDemande.EN_COURS_ANALYSE,
                null,
                null,
                "CLIENT",
                now
        );
        return saved;
    }



    /**
     * Marque l'instruction comme engagée (annule la fenêtre 48 h en base). Appelé au premier traitement
     * ou via {@link #demarrerAnalyse(Long)}.
     */
    private PriseEnCharge releverInstructionMetier(Long demandeId, boolean journaliser) {
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        PriseEnCharge pec = priseEnChargeRepository
                .findActiveVerrouillage(demandeId, banqueUserId, now)
                .orElseThrow(() -> new IllegalStateException(
                        "Demande non verrouillée pour cette banque (ou verrou expiré)"));
        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));
        releverInstructionSiBesoin(pec, demande, journaliser);
        return priseEnChargeRepository.findById(pec.getId()).orElse(pec);
    }

    private void releverInstructionSiBesoin(PriseEnCharge pec, DemandeFinancement demande, boolean journaliser) {
        if (pec.getDecision() != null && "EN_COURS".equalsIgnoreCase(pec.getDecision())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long banqueUserId = SecurityUtils.getCurrentUserId();
        String avant = demande.getStatut();
        pec.setDecision("EN_COURS");
        pec.setDateExpiration(null);
        pec.setDateDebutTraitement(now);
        priseEnChargeRepository.save(pec);
        if (!StatutDemande.EN_COURS_ANALYSE.equalsIgnoreCase(avant)) {
            demande.setStatut(StatutDemande.EN_COURS_ANALYSE);
            demande.setDateDerniereMiseAJour(now);
            demandeRepo.save(demande);
        }
        if (journaliser) {
            historiqueService.enregistrer(
                    demande.getId(),
                    "INSTRUCTION",
                    "Instruction engagée",
                    "Suite sans limite de temps après la première action",
                    avant,
                    StatutDemande.EN_COURS_ANALYSE,
                    banqueUserId,
                    null,
                    "BANQUE",
                    now
            );
        }
    }

    private void completeCamundaPriseEnCharge(

            DemandeFinancement demande, LocalDateTime expiration, Long banqueUserId) {

        camundaWorkflowService.ifPresent(camunda -> {

            String instanceId = demande.getProcessInstanceId();

            if (instanceId != null && !instanceId.isBlank()) {

                camunda.completePriseEnCharge(instanceId, expiration, banqueUserId);

            }

        });

    }



    private void completeCamundaInstruction(DemandeFinancement demande,
                                            String decision,
                                            boolean refusPartiel,
                                            String commentaire,
                                            String motifRefus,
                                            Long banqueUserId) {

        camundaWorkflowService.ifPresent(camunda -> {

            String instanceId = demande.getProcessInstanceId();

            if (instanceId != null && !instanceId.isBlank()) {

                camunda.completeInstruction(instanceId, decision, refusPartiel, commentaire, motifRefus, banqueUserId);

            }

        });

    }

    private ClientIdentityDto fetchClient(DemandeFinancement demande) {
        DossierClient dossier = demande.getDossierClient();
        if (dossier == null || dossier.getClientId() == null) {
            return null;
        }
        try {
            return clientRemoteService.getClientIdentity(dossier.getClientId());
        } catch (Exception ex) {
            log.warn("Client introuvable pour notification demande={} : {}", demande.getId(), ex.getMessage());
            return null;
        }
    }

    private static String resolveNomBanqueCourant() {
        return "Banque partenaire";
    }

}

