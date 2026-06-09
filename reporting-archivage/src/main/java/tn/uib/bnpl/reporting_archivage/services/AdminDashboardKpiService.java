package tn.uib.bnpl.reporting_archivage.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.reporting_archivage.classes.ActionDemandeHistorique;
import tn.uib.bnpl.reporting_archivage.classes.DossierArchive;
import tn.uib.bnpl.reporting_archivage.classes.TypeActionDemande;
import tn.uib.bnpl.reporting_archivage.classes.TypeDecisionFinancement;
import tn.uib.bnpl.reporting_archivage.dto.AdminDashboardKpiSnapshot;
import tn.uib.bnpl.reporting_archivage.feign.GestionDemandeKpiFeign;
import tn.uib.bnpl.reporting_archivage.feign.GestionUtilisateurKpiFeign;
import tn.uib.bnpl.reporting_archivage.feign.dto.DemandesDashboardKpiFeignDto;
import tn.uib.bnpl.reporting_archivage.feign.dto.ReferentielKpiFeignDto;
import tn.uib.bnpl.reporting_archivage.repository.ActionDemandeHistoriqueRepository;
import tn.uib.bnpl.reporting_archivage.repository.DecisionFinancementHistoriqueRepository;
import tn.uib.bnpl.reporting_archivage.repository.DossierArchiveRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardKpiService {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardKpiService.class);

    private final ActionDemandeHistoriqueRepository actionDemandeRepository;
    private final DecisionFinancementHistoriqueRepository decisionRepository;
    private final DossierArchiveRepository dossierArchiveRepository;
    private final GestionDemandeKpiFeign gestionDemandeKpiFeign;
    private final GestionUtilisateurKpiFeign gestionUtilisateurKpiFeign;
    private final ObjectMapper objectMapper;

    public AdminDashboardKpiService(
            ActionDemandeHistoriqueRepository actionDemandeRepository,
            DecisionFinancementHistoriqueRepository decisionRepository,
            DossierArchiveRepository dossierArchiveRepository,
            GestionDemandeKpiFeign gestionDemandeKpiFeign,
            GestionUtilisateurKpiFeign gestionUtilisateurKpiFeign,
            ObjectMapper objectMapper) {
        this.actionDemandeRepository = actionDemandeRepository;
        this.decisionRepository = decisionRepository;
        this.dossierArchiveRepository = dossierArchiveRepository;
        this.gestionDemandeKpiFeign = gestionDemandeKpiFeign;
        this.gestionUtilisateurKpiFeign = gestionUtilisateurKpiFeign;
        this.objectMapper = objectMapper;
    }

    public AdminDashboardKpiSnapshot compute() {
        DemandesDashboardKpiFeignDto demandes = fetchDashboardDemandes();
        ReferentielKpiFeignDto referentiel = fetchReferentiel();

        LocalDateTime debutMois = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long archivesTotal = dossierArchiveRepository.count();
        long demandesTotal = demandes.demandesActivesTotal() + archivesTotal;
        long demandesCeMois = demandes.demandesCeMois() + countArchivesCreationsSince(debutMois);

        BigDecimal montantArchive = nullToZero(dossierArchiveRepository.sumMontantArchive());
        BigDecimal montantActif = nullToZero(demandes.montantTotalActif());
        BigDecimal montantTotal = montantArchive.add(montantActif);
        BigDecimal montantMoyen = demandesTotal > 0
                ? montantTotal.divide(BigDecimal.valueOf(demandesTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long acceptees = demandes.demandesAcceptees()
                + dossierArchiveRepository.countByStatutFinalIgnoreCase("ACCEPTEE");
        long refusees = demandes.demandesRefusees()
                + dossierArchiveRepository.countByStatutFinalIgnoreCase("REFUSEE");
        double tauxAcceptation = acceptees + refusees > 0
                ? round1(100.0 * acceptees / (acceptees + refusees))
                : 0.0;

        PrescoringAggregation prescoring = aggregatePrescoring();
        Double tempsMoyenHeures = computeTempsMoyenTraitementHeures();

        long reponsesBancaires = decisionRepository.countByTypeDecisionIn(List.of(
                TypeDecisionFinancement.ACCEPTEE,
                TypeDecisionFinancement.REFUSEE,
                TypeDecisionFinancement.DEMANDE_COMPLEMENTS
        ));

        long cloturees = archivesTotal;
        Map<String, Long> evolution = buildEvolutionDemandes(demandes);
        Map<String, Long> repartitionStatuts = buildRepartitionStatuts(demandes);
        Map<String, Double> tauxParBanque = buildTauxAcceptationParBanque();
        Map<String, Long> demandesParCommercant = buildDemandesParCommercant(
                demandes.demandesParCommercantUserId(),
                referentiel.commercantsLabels()
        );

        AdminDashboardKpiSnapshot snapshot = new AdminDashboardKpiSnapshot(
                demandesTotal,
                demandesCeMois,
                montantTotal,
                montantMoyen,
                referentiel.clientsInscrits(),
                referentiel.commercantsPartenaires(),
                referentiel.banquesPartenaires(),
                referentiel.utilisateursActifs(),
                referentiel.utilisateursTotal(),
                acceptees,
                refusees,
                tauxAcceptation,
                demandes.demandesEnCoursAnalyse(),
                cloturees,
                prescoring.scoreMoyen(),
                prescoring.risqueFaible(),
                prescoring.risqueMoyen(),
                prescoring.risqueEleve(),
                actionDemandeRepository.countDistinctDemandesRoutees(),
                reponsesBancaires,
                tempsMoyenHeures,
                prescoring.repartitionParZone(),
                evolution,
                repartitionStatuts,
                tauxParBanque,
                demandesParCommercant
        );
        logSnapshot(snapshot, demandes);
        return snapshot;
    }

    private void logSnapshot(AdminDashboardKpiSnapshot kpi, DemandesDashboardKpiFeignDto demandes) {
        log.info(
                "Dashboard KPI — demandesTotal={}, activesBd={}, acceptees={}, clients={}, commercants={}, prescoring V/O/R={}/{}/{}",
                kpi.demandesTotal(),
                demandes.demandesActivesTotal(),
                kpi.demandesAcceptees(),
                kpi.clientsInscrits(),
                kpi.commercantsPartenaires(),
                kpi.prescoringRisqueFaible(),
                kpi.prescoringRisqueMoyen(),
                kpi.prescoringRisqueEleve()
        );
    }

    private Map<String, Long> buildEvolutionDemandes(DemandesDashboardKpiFeignDto demandes) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);
        Map<String, Long> evolution = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            evolution.put(d.toString(), 0L);
        }
        if (demandes.evolutionCreationsParJour() != null) {
            for (Map.Entry<String, Long> entry : demandes.evolutionCreationsParJour().entrySet()) {
                evolution.put(entry.getKey(), entry.getValue());
            }
        }
        for (DossierArchive archive : dossierArchiveRepository.findAll()) {
            LocalDateTime creation = extractDateCreation(archive);
            if (creation == null || creation.toLocalDate().isBefore(start)) {
                continue;
            }
            evolution.merge(creation.toLocalDate().toString(), 1L, Long::sum);
        }
        return evolution;
    }

    private Map<String, Long> buildRepartitionStatuts(DemandesDashboardKpiFeignDto demandes) {
        Map<String, Long> statuts = new LinkedHashMap<>();
        if (demandes.repartitionStatuts() != null) {
            statuts.putAll(demandes.repartitionStatuts());
        }
        for (Object[] row : dossierArchiveRepository.countGroupedByStatutFinal()) {
            if (row[0] == null || row[1] == null) {
                continue;
            }
            long count = row[1] instanceof Number n ? n.longValue() : 0L;
            if (count <= 0) {
                continue;
            }
            String label = libelleStatutFinal(row[0].toString());
            statuts.merge(label, count, Long::sum);
        }
        return statuts;
    }

    private static String libelleStatutFinal(String statutFinal) {
        return switch (statutFinal != null ? statutFinal.trim().toUpperCase(Locale.ROOT) : "") {
            case "ACCEPTEE" -> "Acceptée";
            case "REFUSEE" -> "Refusée";
            default -> statutFinal != null ? statutFinal : "Inconnu";
        };
    }

    private Map<String, Double> buildTauxAcceptationParBanque() {
        Map<String, Double> result = new LinkedHashMap<>();
        for (Object[] row : decisionRepository.acceptanceRateByActeur()) {
            String email = row[0] != null ? row[0].toString() : "Inconnu";
            long acceptees = row[1] instanceof Number n1 ? n1.longValue() : 0L;
            long total = row[2] instanceof Number n2 ? n2.longValue() : 0L;
            if (total <= 0) continue;
            String label = formatBanqueLabel(email);
            result.put(label, round1(100.0 * acceptees / total));
        }
        return result;
    }

    private Map<String, Long> buildDemandesParCommercant(
            Map<String, Long> parUserId,
            Map<String, String> labels
    ) {
        Map<String, String> safeLabels = labels != null ? labels : Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        if (parUserId == null) {
            return result;
        }
        for (Map.Entry<String, Long> entry : parUserId.entrySet()) {
            long userId;
            try {
                userId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException ex) {
                continue;
            }
            long count = entry.getValue() != null ? entry.getValue() : 0L;
            if (userId <= 0 || count <= 0) {
                continue;
            }
            String baseLabel = safeLabels.getOrDefault(String.valueOf(userId), "Commerçant #" + userId);
            String label = result.containsKey(baseLabel) ? baseLabel + " (#" + userId + ")" : baseLabel;
            result.put(label, count);
        }
        return result;
    }

    private long countArchivesCreationsSince(LocalDateTime since) {
        long count = 0;
        for (DossierArchive archive : dossierArchiveRepository.findAll()) {
            LocalDateTime creation = extractDateCreation(archive);
            if (creation != null && !creation.isBefore(since)) {
                count++;
            }
        }
        return count;
    }

    private static String formatBanqueLabel(String email) {
        if (email == null || email.isBlank() || "Inconnu".equalsIgnoreCase(email)) {
            return "Inconnu";
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        if (local.length() > 18) {
            return local.substring(0, 16) + "…";
        }
        return local;
    }

    private DemandesDashboardKpiFeignDto fetchDashboardDemandes() {
        try {
            DemandesDashboardKpiFeignDto dto = gestionDemandeKpiFeign.dashboardDemandes();
            return dto != null ? dto : emptyDashboardDemandes();
        } catch (Exception ex) {
            log.warn("KPI dashboard demandes indisponible (gestion-demande): {} — {}", ex.getClass().getSimpleName(), ex.getMessage());
            return emptyDashboardDemandes();
        }
    }

    private static DemandesDashboardKpiFeignDto emptyDashboardDemandes() {
        return new DemandesDashboardKpiFeignDto(
                0, 0, 0, 0, 0, 0, 0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private ReferentielKpiFeignDto fetchReferentiel() {
        try {
            return gestionUtilisateurKpiFeign.referentiel();
        } catch (Exception ex) {
            log.warn("KPI référentiel indisponible (gestion-utilisateur): {} — {}", ex.getClass().getSimpleName(), ex.getMessage());
            return new ReferentielKpiFeignDto(0, 0, 0, 0, 0, Map.of());
        }
    }

    private PrescoringAggregation aggregatePrescoring() {
        List<ActionDemandeHistorique> scoringEvents =
                actionDemandeRepository.findByTypeAction(TypeActionDemande.SCORING);
        Map<Long, String> zoneParDemande = new LinkedHashMap<>();
        Map<Long, Integer> scoreParDemande = new LinkedHashMap<>();

        for (ActionDemandeHistorique event : scoringEvents) {
            if (event.getDemandeId() == null) {
                continue;
            }
            parsePrescoringDetails(event.getDetailsJson(), event.getDemandeId(), zoneParDemande, scoreParDemande);
        }

        Map<String, Long> repartition = new LinkedHashMap<>();
        long vert = 0;
        long orange = 0;
        long rouge = 0;
        for (String zone : zoneParDemande.values()) {
            repartition.merge(zone, 1L, Long::sum);
            switch (zone) {
                case "vert" -> vert++;
                case "orange" -> orange++;
                case "rouge" -> rouge++;
                default -> { }
            }
        }

        double scoreMoyen = scoreParDemande.isEmpty()
                ? 0.0
                : scoreParDemande.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);

        return new PrescoringAggregation(round1(scoreMoyen), vert, orange, rouge, repartition);
    }

    private void parsePrescoringDetails(
            String detailsJson,
            Long demandeId,
            Map<Long, String> zoneParDemande,
            Map<Long, Integer> scoreParDemande
    ) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            if (root.hasNonNull("zoneCode")) {
                zoneParDemande.put(demandeId, normalizeZone(root.get("zoneCode").asText()));
            }
            if (root.hasNonNull("score")) {
                scoreParDemande.put(demandeId, root.get("score").asInt());
            }
        } catch (Exception ex) {
            log.debug("detailsJson prescoring illisible demande={}", demandeId);
        }
    }

    private Double computeTempsMoyenTraitementHeures() {
        List<DossierArchive> archives = dossierArchiveRepository.findAll();
        if (archives.isEmpty()) {
            return null;
        }
        long totalMinutes = 0;
        long counted = 0;
        for (DossierArchive archive : archives) {
            LocalDateTime cloture = archive.getDateCloture();
            LocalDateTime creation = extractDateCreation(archive);
            if (cloture == null || creation == null || cloture.isBefore(creation)) {
                continue;
            }
            totalMinutes += Duration.between(creation, cloture).toMinutes();
            counted++;
        }
        if (counted == 0) {
            return null;
        }
        return round1(totalMinutes / (60.0 * counted));
    }

    private LocalDateTime extractDateCreation(DossierArchive archive) {
        String snapshot = archive.getSnapshotJson();
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(snapshot);
            if (root.hasNonNull("dateCreation")) {
                return LocalDateTime.parse(root.get("dateCreation").asText());
            }
        } catch (Exception ex) {
            log.debug("snapshot sans dateCreation demande={}", archive.getDemandeId());
        }
        return null;
    }

    private static String normalizeZone(String zone) {
        if (zone == null) {
            return "inconnu";
        }
        return zone.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record PrescoringAggregation(
            double scoreMoyen,
            long risqueFaible,
            long risqueMoyen,
            long risqueEleve,
            Map<String, Long> repartitionParZone
    ) {}
}
