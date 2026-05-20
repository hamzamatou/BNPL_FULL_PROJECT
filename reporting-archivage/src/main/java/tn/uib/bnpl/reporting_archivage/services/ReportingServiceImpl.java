package tn.uib.bnpl.reporting_archivage.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.*;
import tn.uib.bnpl.reporting_archivage.dto.ActionDemandeResumeDto;
import tn.uib.bnpl.reporting_archivage.dto.DashboardReportingDto;
import tn.uib.bnpl.reporting_archivage.repository.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportingServiceImpl implements ReportingService {

    private final ActionDemandeHistoriqueRepository actionDemandeRepository;
    private final DecisionFinancementHistoriqueRepository decisionRepository;
    private final AccesPlateformeHistoriqueRepository accesRepository;
    private final ActionDocumentHistoriqueRepository actionDocumentRepository;
    private final DossierArchiveRepository dossierArchiveRepository;

    public ReportingServiceImpl(
            ActionDemandeHistoriqueRepository actionDemandeRepository,
            DecisionFinancementHistoriqueRepository decisionRepository,
            AccesPlateformeHistoriqueRepository accesRepository,
            ActionDocumentHistoriqueRepository actionDocumentRepository,
            DossierArchiveRepository dossierArchiveRepository) {
        this.actionDemandeRepository = actionDemandeRepository;
        this.decisionRepository = decisionRepository;
        this.accesRepository = accesRepository;
        this.actionDocumentRepository = actionDocumentRepository;
        this.dossierArchiveRepository = dossierArchiveRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardReportingDto getDashboard() {
        LocalDateTime depuis24h = LocalDateTime.now().minusHours(24);
        LocalDateTime depuis30j = LocalDateTime.now().minusDays(30);

        long actions24h = actionDemandeRepository.countByDateActionAfter(depuis24h);
        long decisions24h = decisionRepository.findByDateDecisionBetween(
                depuis24h, LocalDateTime.now(), Pageable.unpaged()).getTotalElements();
        long accesSuspects24h = accesRepository.countBySuspectTrueAndDateAccesAfter(depuis24h);
        long archivesTotal = dossierArchiveRepository.count();
        long archives30j = dossierArchiveRepository.countByDateArchivageAfter(depuis30j);

        Map<String, Long> repartitionActions = actionDemandeRepository.findAll().stream()
                .collect(Collectors.groupingBy(a -> a.getTypeAction().name(), Collectors.counting()));

        Map<String, Long> repartitionDecisions = decisionRepository.findAll().stream()
                .collect(Collectors.groupingBy(d -> d.getTypeDecision().name(), Collectors.counting()));

        List<ActionDemandeResumeDto> dernieres = actionDemandeRepository.findTop10ByOrderByDateActionDesc()
                .stream()
                .map(a -> new ActionDemandeResumeDto(
                        a.getId(), a.getDemandeId(), a.getReferenceDemande(),
                        a.getTypeAction().name(), a.getLibelle(), a.getActeurEmail(), a.getDateAction()))
                .toList();

        return new DashboardReportingDto(
                actions24h, decisions24h, accesSuspects24h, archivesTotal, archives30j,
                new LinkedHashMap<>(repartitionActions),
                new LinkedHashMap<>(repartitionDecisions),
                dernieres
        );
    }

    @Override
    public Page<ActionDemandeHistorique> getActionsDemandes(Long demandeId, String type,
                                                             LocalDateTime debut, LocalDateTime fin,
                                                             Pageable pageable) {
        if (demandeId != null) {
            return actionDemandeRepository.findByDemandeId(demandeId, pageable);
        }
        if (type != null && debut != null && fin != null) {
            return actionDemandeRepository.findByTypeActionAndDateActionBetween(
                    TypeActionDemande.valueOf(type.toUpperCase()), debut, fin, pageable);
        }
        if (debut != null && fin != null) {
            return actionDemandeRepository.findByDateActionBetween(debut, fin, pageable);
        }
        return actionDemandeRepository.findAll(pageable);
    }

    @Override
    public Page<ActionDocumentHistorique> getActionsDocuments(Long demandeId, String objectKey,
                                                               LocalDateTime debut, LocalDateTime fin,
                                                               Pageable pageable) {
        if (demandeId != null) {
            return actionDocumentRepository.findByDemandeId(demandeId, pageable);
        }
        if (objectKey != null && !objectKey.isBlank()) {
            return actionDocumentRepository.findByObjectKey(objectKey, pageable);
        }
        if (debut != null && fin != null) {
            return actionDocumentRepository.findByDateActionBetween(debut, fin, pageable);
        }
        return actionDocumentRepository.findAll(pageable);
    }

    @Override
    public Page<AccesPlateformeHistorique> getAcces(Long userId, Boolean suspectOnly,
                                                     LocalDateTime debut, LocalDateTime fin,
                                                     Pageable pageable) {
        LocalDateTime d = debut != null ? debut : LocalDateTime.now().minusDays(30);
        LocalDateTime f = fin != null ? fin : LocalDateTime.now();

        if (Boolean.TRUE.equals(suspectOnly)) {
            return accesRepository.findBySuspectTrueAndDateAccesBetween(d, f, pageable);
        }
        if (userId != null) {
            return accesRepository.findByUserIdAndDateAccesBetween(userId, d, f, pageable);
        }
        return accesRepository.findByDateAccesBetween(d, f, pageable);
    }

    @Override
    public Page<DecisionFinancementHistorique> getDecisions(Long demandeId, String type,
                                                             LocalDateTime debut, LocalDateTime fin,
                                                             Pageable pageable) {
        if (demandeId != null) {
            return decisionRepository.findByDemandeId(demandeId, pageable);
        }
        if (type != null && debut != null && fin != null) {
            return decisionRepository.findByTypeDecisionAndDateDecisionBetween(
                    TypeDecisionFinancement.valueOf(type.toUpperCase()), debut, fin, pageable);
        }
        if (debut != null && fin != null) {
            return decisionRepository.findByDateDecisionBetween(debut, fin, pageable);
        }
        return decisionRepository.findAll(pageable);
    }
}
