package tn.uib.bnpl.gestion_demande.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_demande.classes.StatutDemande;
import tn.uib.bnpl.gestion_demande.dto.DemandesActivesKpiDto;
import tn.uib.bnpl.gestion_demande.dto.DemandesDashboardKpiDto;
import tn.uib.bnpl.gestion_demande.repository.DemandeFinancementRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DemandeKpiService {

    private final DemandeFinancementRepository demandeRepo;

    public DemandeKpiService(DemandeFinancementRepository demandeRepo) {
        this.demandeRepo = demandeRepo;
    }

    public DemandesActivesKpiDto snapshotDemandesActives() {
        DemandesDashboardKpiDto dash = dashboardDemandes();
        return new DemandesActivesKpiDto(
                dash.demandesActivesTotal(),
                dash.demandesEnCoursAnalyse(),
                dash.demandesEnAttenteComplement(),
                dash.demandesSoumises(),
                dash.montantTotalActif()
        );
    }

    public DemandesDashboardKpiDto dashboardDemandes() {
        LocalDate today = LocalDate.now();
        LocalDateTime since30 = today.minusDays(29).atStartOfDay();
        LocalDateTime debutMois = today.withDayOfMonth(1).atStartOfDay();

        long total = demandeRepo.count();
        long ceMois = demandeRepo.countByDateCreationGreaterThanEqual(debutMois);
        long enAnalyse = demandeRepo.countByStatutIgnoreCase(StatutDemande.EN_COURS_ANALYSE);
        long enAttenteComplement = demandeRepo.countByStatutIgnoreCase(StatutDemande.EN_ATTENTE_COMPLEMENT);
        long soumises = demandeRepo.countByStatutIgnoreCase(StatutDemande.SOUMISE);
        long acceptees = demandeRepo.countByStatutIgnoreCase(StatutDemande.ACCEPTEE);
        long refusees = demandeRepo.countByStatutIgnoreCase(StatutDemande.REFUSEE);
        BigDecimal montant = demandeRepo.sumMontantActif();
        if (montant == null) {
            montant = BigDecimal.ZERO;
        }

        return new DemandesDashboardKpiDto(
                total,
                ceMois,
                enAnalyse,
                enAttenteComplement,
                soumises,
                acceptees,
                refusees,
                montant,
                repartitionStatutsActifs(),
                buildEvolutionMap(since30),
                buildParCommercantMap()
        );
    }

    public Map<String, Long> repartitionStatutsActifs() {
        Map<String, Long> statuts = new LinkedHashMap<>();
        for (Object[] row : demandeRepo.countGroupedByStatut()) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            if (count <= 0) {
                continue;
            }
            String label = libelleStatut(row[0].toString());
            statuts.merge(label, count, Long::sum);
        }
        return statuts;
    }

    private Map<String, Long> buildEvolutionMap(LocalDateTime since) {
        LocalDate start = since.toLocalDate();
        LocalDate today = LocalDate.now();
        Map<String, Long> evolution = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            evolution.put(d.toString(), 0L);
        }
        for (LocalDateTime createdAt : demandeRepo.findCreationDatesSince(since)) {
            LocalDate day = createdAt.toLocalDate();
            if (day.isBefore(start)) {
                continue;
            }
            evolution.merge(day.toString(), 1L, Long::sum);
        }
        return evolution;
    }

    private Map<String, Long> buildParCommercantMap() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : demandeRepo.countGroupedByCommercantUserId()) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            long userId = row[0] instanceof Number n ? n.longValue() : 0L;
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            if (userId <= 0 || count <= 0) {
                continue;
            }
            result.put(String.valueOf(userId), count);
        }
        return result;
    }

    private static String libelleStatut(String statut) {
        return switch (StatutDemande.upper(statut)) {
            case StatutDemande.CREE -> "Créée";
            case StatutDemande.EN_ATTENTE_CONSENTEMENT -> "En attente consentement";
            case StatutDemande.EN_COURS_PRESCORING -> "Prescoring en cours";
            case StatutDemande.SOUMISE -> "Soumise";
            case StatutDemande.EN_COURS_ANALYSE -> "En cours d'analyse";
            case StatutDemande.EN_ATTENTE_COMPLEMENT -> "En attente compléments";
            case StatutDemande.ACCEPTEE -> "Acceptée";
            case StatutDemande.REFUSEE -> "Refusée";
            case StatutDemande.REJETEE_AUTO, "REJET_AUTO" -> "Rejetée (auto)";
            case StatutDemande.ANNULEE -> "Annulée";
            case StatutDemande.CLOTUREE -> "Clôturée";
            default -> statut;
        };
    }
}
