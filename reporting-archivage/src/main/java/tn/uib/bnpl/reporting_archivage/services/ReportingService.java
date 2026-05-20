package tn.uib.bnpl.reporting_archivage.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.uib.bnpl.reporting_archivage.classes.*;
import tn.uib.bnpl.reporting_archivage.dto.DashboardReportingDto;

import java.time.LocalDateTime;

public interface ReportingService {

    DashboardReportingDto getDashboard();

    Page<ActionDemandeHistorique> getActionsDemandes(Long demandeId, String type,
                                                    LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<ActionDocumentHistorique> getActionsDocuments(Long demandeId, String objectKey,
                                                      LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<AccesPlateformeHistorique> getAcces(Long userId, Boolean suspectOnly,
                                              LocalDateTime debut, LocalDateTime fin, Pageable pageable);

    Page<DecisionFinancementHistorique> getDecisions(Long demandeId, String type,
                                                      LocalDateTime debut, LocalDateTime fin, Pageable pageable);
}
