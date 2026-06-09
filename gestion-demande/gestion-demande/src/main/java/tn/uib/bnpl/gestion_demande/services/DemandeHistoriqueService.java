package tn.uib.bnpl.gestion_demande.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import tn.uib.bnpl.gestion_demande.classes.DemandeFinancement;
import tn.uib.bnpl.gestion_demande.classes.PrescoringScore;
import tn.uib.bnpl.gestion_demande.classes.Recommandation;
import tn.uib.bnpl.gestion_demande.classes.StatutDemande;
import tn.uib.bnpl.gestion_demande.client.ReportingArchivageClient;
import tn.uib.bnpl.gestion_demande.dto.ActionDemandeHistoriqueViewDto;
import tn.uib.bnpl.gestion_demande.dto.DemandeCompleteResponse;
import tn.uib.bnpl.gestion_demande.dto.audit.AuditEventPayload;
import tn.uib.bnpl.gestion_demande.dto.audit.AuditEventRequest;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;
import tn.uib.bnpl.gestion_demande.security.SecurityUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class DemandeHistoriqueService {

    private static final Logger log = LoggerFactory.getLogger(DemandeHistoriqueService.class);

    private final AuditPublisher auditPublisher;
    private final ReportingArchivageClient reportingArchivageClient;
    private final DemandeFinancementRepository demandeRepo;
    private final ObjectMapper objectMapper;

    public DemandeHistoriqueService(
            AuditPublisher auditPublisher,
            ReportingArchivageClient reportingArchivageClient,
            DemandeFinancementRepository demandeRepo,
            ObjectMapper objectMapper) {
        this.auditPublisher = auditPublisher;
        this.reportingArchivageClient = reportingArchivageClient;
        this.demandeRepo = demandeRepo;
        this.objectMapper = objectMapper;
    }

    public void enregistrer(
            Long demandeId,
            String type,
            String libelle,
            String detail,
            String statutAvant,
            String statutApres
    ) {
        enregistrer(demandeId, type, libelle, detail, statutAvant, statutApres,
                currentActeurIdOrNull(), currentActeurEmailOrNull(), currentActeurRoleOrNull(), LocalDateTime.now());
    }

    public void enregistrer(
            Long demandeId,
            String type,
            String libelle,
            String detail,
            String statutAvant,
            String statutApres,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole,
            LocalDateTime dateEvenement
    ) {
        enregistrer(demandeId, type, libelle, detail, statutAvant, statutApres,
                acteurUserId, acteurEmail, acteurRole, dateEvenement, null);
    }

    public void enregistrer(
            Long demandeId,
            String type,
            String libelle,
            String detail,
            String statutAvant,
            String statutApres,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole,
            LocalDateTime dateEvenement,
            Map<String, Object> iaEnrichissement
    ) {
        String referenceDemande = demandeRepo.findById(demandeId)
                .map(DemandeFinancement::getReferenceDemande)
                .orElse(null);
        String detailsJson = buildDetailsJson(type, detail, iaEnrichissement);
        String typeAction = ActionDemandeTypeMapper.mapTypeAction(type, statutApres);
        LocalDateTime occurredAt = dateEvenement != null ? dateEvenement : LocalDateTime.now();

        publierActionDemande(
                demandeId,
                referenceDemande,
                typeAction,
                libelle,
                detailsJson,
                acteurUserId,
                acteurEmail,
                acteurRole,
                statutAvant,
                statutApres,
                occurredAt
        );

        ActionDemandeTypeMapper.mapTypeDecision(type, statutApres).ifPresent(decisionType ->
                publierDecisionFinancement(
                        demandeId,
                        referenceDemande,
                        decisionType,
                        libelle,
                        detailsJson,
                        acteurUserId,
                        acteurEmail,
                        acteurRole,
                        occurredAt
                )
        );
    }

    @Transactional(readOnly = true)
    public List<DemandeCompleteResponse.HistoriqueEvenementDto> listerPourDemande(DemandeFinancement demande) {
        if (demande == null || demande.getId() == null) {
            return List.of();
        }

        try {
            List<ActionDemandeHistoriqueViewDto> actions =
                    reportingArchivageClient.listerActionsDemande(demande.getId());
            if (actions != null && !actions.isEmpty()) {
                return actions.stream()
                        .filter(action -> !"CLOTURE".equalsIgnoreCase(action.typeAction()))
                        .map(this::toDto)
                        .toList();
            }
        } catch (RestClientException ex) {
            log.warn("Lecture historique reporting-archivage impossible pour demande={} — fallback local",
                    demande.getId(), ex);
        }

        return reconstruireDepuisDemande(demande);
    }

    private void publierActionDemande(
            Long demandeId,
            String referenceDemande,
            String typeAction,
            String libelle,
            String detailsJson,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole,
            String statutAvant,
            String statutApres,
            LocalDateTime occurredAt
    ) {
        AuditEventRequest request = new AuditEventRequest(
                "ACTION_DEMANDE",
                correlationId(demandeId, typeAction, occurredAt),
                null,
                occurredAt,
                AuditEventPayload.actionDemande(
                        demandeId,
                        referenceDemande,
                        typeAction,
                        libelle,
                        detailsJson,
                        acteurUserId,
                        acteurEmail,
                        acteurRole,
                        statutAvant,
                        statutApres,
                        occurredAt
                )
        );
        try {
            auditPublisher.publier(request);
        } catch (IllegalStateException ex) {
            throw new IllegalStateException(
                    "Impossible d'enregistrer l'action demande dans reporting-archivage pour demande=" + demandeId,
                    ex);
        }
    }

    private static String correlationId(Long demandeId, String type, LocalDateTime occurredAt) {
        return demandeId + "-" + type + "-" + occurredAt + "-" + UUID.randomUUID();
    }

    private void publierDecisionFinancement(
            Long demandeId,
            String referenceDemande,
            String typeDecision,
            String libelle,
            String detailsJson,
            Long acteurUserId,
            String acteurEmail,
            String acteurRole,
            LocalDateTime occurredAt
    ) {
        AuditEventRequest request = new AuditEventRequest(
                "DECISION_FINANCEMENT",
                correlationId(demandeId, typeDecision, occurredAt),
                null,
                occurredAt,
                AuditEventPayload.decisionFinancement(
                        demandeId,
                        referenceDemande,
                        typeDecision,
                        libelle,
                        detailsJson,
                        acteurUserId,
                        acteurEmail,
                        acteurRole,
                        occurredAt
                )
        );
        try {
            auditPublisher.publier(request);
        } catch (IllegalStateException ex) {
            throw new IllegalStateException(
                    "Impossible d'enregistrer la décision dans reporting-archivage pour demande=" + demandeId,
                    ex);
        }
    }

    private DemandeCompleteResponse.HistoriqueEvenementDto toDto(ActionDemandeHistoriqueViewDto action) {
        String type = action.typeSource() != null && !action.typeSource().isBlank()
                ? action.typeSource()
                : action.typeAction();
        return new DemandeCompleteResponse.HistoriqueEvenementDto(
                type,
                action.libelle(),
                action.detail(),
                action.statutAvant(),
                action.statutApres(),
                action.dateAction()
        );
    }

    private String buildDetailsJson(String typeSource, String detail, Map<String, Object> iaEnrichissement) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (typeSource != null && !typeSource.isBlank()) {
            map.put("typeSource", typeSource);
        }
        if (detail != null && !detail.isBlank()) {
            map.put("detail", detail);
        }
        if (iaEnrichissement != null && !iaEnrichissement.isEmpty()) {
            map.putAll(iaEnrichissement);
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private List<DemandeCompleteResponse.HistoriqueEvenementDto> reconstruireDepuisDemande(DemandeFinancement d) {
        List<DemandeCompleteResponse.HistoriqueEvenementDto> items = new ArrayList<>();

        if (d.getDateCreation() != null) {
            items.add(evt("CREATION", "Demande créée",
                    "Dossier BNPL enregistré par le commerçant",
                    null, "CREE", d.getDateCreation()));
        }

        Recommandation reco = d.getRecommandation();
        if (reco != null && reco.getGeneratedAt() != null) {
            items.add(evt("RECOMMANDATION", "Recommandations générées",
                    "Analyse IA et propositions de financement",
                    "CREE", "CREE", reco.getGeneratedAt()));
        }

        String statut = upper(d.getStatut());
        LocalDateTime maj = d.getDateDerniereMiseAJour() != null ? d.getDateDerniereMiseAJour() : d.getDateCreation();

        if (isAtLeast(statut, "EN_ATTENTE_CONSENTEMENT")) {
            items.add(evt("CONSENTEMENT_ENVOYE", "Consentement client demandé",
                    "E-mail envoyé au client pour validation",
                    "CREE", "EN_ATTENTE_CONSENTEMENT", maj));
        }

        PrescoringScore ps = d.getPrescoringScore();
        if (ps != null && ps.getComputedAt() != null) {
            items.add(evt("PRESCORING", "Prescoring réalisé",
                    "Score " + ps.getScore() + " — zone " + ps.getZoneCode(),
                    "SOUMISE", "SOUMISE", ps.getComputedAt()));
        }

        if (isAtLeast(statut, "SOUMISE") && !"EN_ATTENTE_CONSENTEMENT".equals(statut) && !"CREE".equals(statut)) {
            items.add(evt("CONSENTEMENT_VALIDE", "Consentement client validé",
                    "La demande est soumise pour traitement",
                    "EN_ATTENTE_CONSENTEMENT", "SOUMISE", maj));
        }

        if ("EN_COURS_ANALYSE".equals(statut)) {
            items.add(evt("ANALYSE", "Demande en analyse",
                    "Prise en charge par un analyste bancaire",
                    StatutDemande.SOUMISE, StatutDemande.EN_COURS_ANALYSE, maj));
        }
        if ("EN_ATTENTE_COMPLEMENT".equals(statut)) {
            items.add(evt("COMPLEMENTS", "En attente de compléments",
                    "Informations complémentaires demandées au client",
                    StatutDemande.EN_COURS_ANALYSE, StatutDemande.EN_ATTENTE_COMPLEMENT, maj));
        }
        if ("ACCEPTEE".equals(statut)) {
            items.add(evt("DECISION", "Demande acceptée",
                    "Financement accordé",
                    StatutDemande.EN_COURS_ANALYSE, StatutDemande.ACCEPTEE, maj));
        }
        if ("REFUSEE".equals(statut)) {
            items.add(evt("DECISION", "Demande refusée",
                    "Décision de financement défavorable",
                    StatutDemande.EN_COURS_ANALYSE, StatutDemande.REFUSEE, maj));
        }
        if (StatutDemande.isRejetAutoPrescoring(statut)) {
            items.add(evt("REJET_AUTO", "Rejet automatique prescoring",
                    "PD > 60 % — demande non routée vers les banques",
                    StatutDemande.EN_COURS_PRESCORING, StatutDemande.REJETEE_AUTO, maj));
        }
        if ("ANNULEE".equals(statut)) {
            items.add(evt("ANNULATION", "Demande annulée",
                    "Annulation par le commerçant",
                    null, "ANNULEE", maj));
        }

        items.sort(Comparator.comparing(DemandeCompleteResponse.HistoriqueEvenementDto::dateEvenement));
        return items;
    }

    private static DemandeCompleteResponse.HistoriqueEvenementDto evt(
            String type, String libelle, String detail,
            String avant, String apres, LocalDateTime date
    ) {
        return new DemandeCompleteResponse.HistoriqueEvenementDto(type, libelle, detail, avant, apres, date);
    }

    private static Long currentActeurIdOrNull() {
        try {
            return SecurityUtils.getCurrentUserId();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String currentActeurRoleOrNull() {
        try {
            return SecurityUtils.getCurrentUserRole();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String currentActeurEmailOrNull() {
        try {
            return SecurityUtils.getCurrentUserEmail();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private static boolean isAtLeast(String statut, String seuil) {
        List<String> order = List.of(
                "CREE",
                "EN_ATTENTE_CONSENTEMENT",
                "EN_COURS_PRESCORING",
                "SOUMISE",
                "EN_COURS_ANALYSE",
                "EN_ATTENTE_COMPLEMENT",
                "ACCEPTEE",
                "REJETEE_AUTO",
                "REJET_AUTO",
                "REFUSEE",
                "CLOTUREE",
                "ANNULEE"
        );
        int s = order.indexOf(statut);
        int t = order.indexOf(seuil);
        if (s < 0 || t < 0) {
            return statut.contains(seuil);
        }
        return s >= t;
    }
}
