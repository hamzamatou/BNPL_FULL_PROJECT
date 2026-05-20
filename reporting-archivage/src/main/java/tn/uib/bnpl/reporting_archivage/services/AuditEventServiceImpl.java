package tn.uib.bnpl.reporting_archivage.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.*;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventPayload;
import tn.uib.bnpl.reporting_archivage.dto.AuditEventRequest;
import tn.uib.bnpl.reporting_archivage.repository.*;

import java.time.LocalDateTime;

@Service
public class AuditEventServiceImpl implements AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventServiceImpl.class);

    private final DecisionFinancementHistoriqueRepository decisionRepository;
    private final AccesPlateformeHistoriqueRepository accesRepository;
    private final ActionDemandeHistoriqueRepository actionDemandeRepository;
    private final ActionDocumentHistoriqueRepository actionDocumentRepository;
    private final ArchivageService archivageService;

    public AuditEventServiceImpl(
            DecisionFinancementHistoriqueRepository decisionRepository,
            AccesPlateformeHistoriqueRepository accesRepository,
            ActionDemandeHistoriqueRepository actionDemandeRepository,
            ActionDocumentHistoriqueRepository actionDocumentRepository,
            ArchivageService archivageService) {
        this.decisionRepository = decisionRepository;
        this.accesRepository = accesRepository;
        this.actionDemandeRepository = actionDemandeRepository;
        this.actionDocumentRepository = actionDocumentRepository;
        this.archivageService = archivageService;
    }

    @Override
    @Transactional
    public void traiterEvenement(AuditEventRequest request) {
        String type = request.eventType().toUpperCase();
        AuditEventPayload p = request.payload();
        LocalDateTime occurredAt = request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now();
        String correlationId = request.correlationId();

        switch (type) {
            case "DECISION_FINANCEMENT" -> enregistrerDecision(p, occurredAt, correlationId);
            case "ACCES_PLATEFORME" -> enregistrerAcces(p, occurredAt, correlationId);
            case "ACTION_DEMANDE" -> enregistrerActionDemande(p, occurredAt, correlationId);
            case "ACTION_DOCUMENT" -> enregistrerActionDocument(p, occurredAt, correlationId);
            case "ARCHIVAGE_DOSSIER" -> archivageService.archiverDepuisEvenement(p, occurredAt, correlationId);
            default -> log.warn("Type d'événement audit inconnu: {}", type);
        }
    }

    private void enregistrerDecision(AuditEventPayload p, LocalDateTime date, String correlationId) {
        if (p.demandeId() == null) {
            throw new IllegalArgumentException("demandeId obligatoire pour DECISION_FINANCEMENT");
        }
        TypeDecisionFinancement typeDecision = parseEnum(
                p.type(), TypeDecisionFinancement.class, TypeDecisionFinancement.AUTRE);

        DecisionFinancementHistorique entity = new DecisionFinancementHistorique();
        entity.setDemandeId(p.demandeId());
        entity.setReferenceDemande(p.referenceDemande());
        entity.setTypeDecision(typeDecision);
        entity.setLibelle(p.libelle() != null ? p.libelle() : typeDecision.name());
        entity.setDetailsJson(p.detailsJson());
        entity.setActeurUserId(p.acteurUserId());
        entity.setActeurEmail(p.acteurEmail());
        entity.setActeurRole(p.acteurRole());
        entity.setEtapeWorkflow(p.etapeWorkflow());
        entity.setCorrelationId(correlationId);
        entity.setDateDecision(date);
        decisionRepository.save(entity);
    }

    private void enregistrerAcces(AuditEventPayload p, LocalDateTime date, String correlationId) {
        TypeAccesPlateforme typeAcces = parseEnum(
                p.type(), TypeAccesPlateforme.class, TypeAccesPlateforme.AUTRE);

        AccesPlateformeHistorique entity = new AccesPlateformeHistorique();
        entity.setUserId(p.userId());
        entity.setUserEmail(p.userEmail());
        entity.setUserRole(p.userRole());
        entity.setTypeAcces(typeAcces);
        entity.setDescription(p.libelle() != null ? p.libelle() : typeAcces.name());
        entity.setAdresseIp(p.adresseIp());
        entity.setUserAgent(p.userAgent());
        entity.setEndpoint(p.endpoint());
        entity.setMethodeHttp(p.methodeHttp());
        entity.setSuspect(Boolean.TRUE.equals(p.suspect()));
        entity.setDetailsJson(p.detailsJson());
        entity.setCorrelationId(correlationId);
        entity.setDateAcces(date);
        accesRepository.save(entity);
    }

    private void enregistrerActionDemande(AuditEventPayload p, LocalDateTime date, String correlationId) {
        if (p.demandeId() == null) {
            throw new IllegalArgumentException("demandeId obligatoire pour ACTION_DEMANDE");
        }
        TypeActionDemande typeAction = parseEnum(
                p.type(), TypeActionDemande.class, TypeActionDemande.AUTRE);

        ActionDemandeHistorique entity = new ActionDemandeHistorique();
        entity.setDemandeId(p.demandeId());
        entity.setReferenceDemande(p.referenceDemande());
        entity.setTypeAction(typeAction);
        entity.setLibelle(p.libelle() != null ? p.libelle() : typeAction.name());
        entity.setStatutAvant(p.statutAvant());
        entity.setStatutApres(p.statutApres());
        entity.setActeurUserId(p.acteurUserId());
        entity.setActeurEmail(p.acteurEmail());
        entity.setActeurRole(p.acteurRole());
        entity.setDetailsJson(p.detailsJson());
        entity.setCorrelationId(correlationId);
        entity.setDateAction(date);
        actionDemandeRepository.save(entity);
    }

    private void enregistrerActionDocument(AuditEventPayload p, LocalDateTime date, String correlationId) {
        if (p.demandeId() == null) {
            throw new IllegalArgumentException("demandeId obligatoire pour ACTION_DOCUMENT");
        }
        TypeActionDocument typeAction = parseEnum(
                p.type(), TypeActionDocument.class, TypeActionDocument.AUTRE);

        ActionDocumentHistorique entity = new ActionDocumentHistorique();
        entity.setDemandeId(p.demandeId());
        entity.setReferenceDemande(p.referenceDemande());
        entity.setDocumentId(p.documentId());
        entity.setObjectKey(p.objectKey());
        entity.setTypeDocument(p.typeDocument());
        entity.setTypeAction(typeAction);
        entity.setLibelle(p.libelle() != null ? p.libelle() : typeAction.name());
        entity.setActeurUserId(p.acteurUserId());
        entity.setActeurEmail(p.acteurEmail());
        entity.setActeurRole(p.acteurRole());
        entity.setDetailsJson(p.detailsJson());
        entity.setCorrelationId(correlationId);
        entity.setDateAction(date);
        actionDocumentRepository.save(entity);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass, E defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }
}
