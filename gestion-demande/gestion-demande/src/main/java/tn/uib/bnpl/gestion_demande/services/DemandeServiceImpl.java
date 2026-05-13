package tn.uib.bnpl.gestion_demande.services;

import io.minio.MinioClient;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tn.uib.bnpl.gestion_demande.classes.*;
import tn.uib.bnpl.gestion_demande.dto.CreationDemandeCompleteRequest;
import tn.uib.bnpl.gestion_demande.dto.DernierDossierFinancierResponse;
import tn.uib.bnpl.gestion_demande.repository.*;
import tn.uib.bnpl.gestion_demande.security.SecurityUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class DemandeServiceImpl implements DemandeService {
    private static final Logger log = LoggerFactory.getLogger(DemandeServiceImpl.class);

    private final DossierClientRepository dossierRepo;
    private final DemandeFinancementRepository demandeRepo;
    private final DocumentDossierRepository documentRepo;
    private final ClientRemoteService clientRemoteService;
    private final ActionClientService actionClientService;
    private final MinioClient minioClient;

    @Value("${minio.bucket:bnpl-documents}")
    private String bucket;

    // URL front pour lien email consentement
    private final String frontBaseUrl = "http://localhost:4200";

    public DemandeServiceImpl(DossierClientRepository dossierRepo,
                              DemandeFinancementRepository demandeRepo,
                              DocumentDossierRepository documentRepo,
                              ClientRemoteService clientRemoteService,
                              MinioClient minioClient,
                              ActionClientService actionClientService) {
        this.dossierRepo = dossierRepo;
        this.demandeRepo = demandeRepo;
        this.documentRepo = documentRepo;
        this.clientRemoteService = clientRemoteService;
        this.minioClient = minioClient;
        this.actionClientService = actionClientService;
    }

    @Override
    public DemandeFinancement creerDemandeComplete(CreationDemandeCompleteRequest request) {
       // validateCreationRequest(request);

        Long commercantUserId = SecurityUtils.getCurrentUserId();

        Long clientId;
        try {
            // Si le client existe déjà (CIN), on réutilise son id pour éviter les erreurs d'unicité.
            clientId = clientRemoteService.getClientIdByCin(request.getCin());
            // Et on met à jour ses informations avec les données saisies si nécessaire.
            clientRemoteService.modifierClient(
                    clientId,
                    request.getNom(),
                    request.getPrenom(),
                    request.getEmail(),
                    request.getTelephone(),
                    request.getCin(),
                    request.getAdresse(),
                    request.getSexe(),
                    request.getProfession(),
                    request.getEmployeur(),
                    request.getDateNaissance()
            );
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (!msg.startsWith("Client introuvable pour CIN")) {
                throw ex;
            }
            clientId = clientRemoteService.creerClient(
                    request.getNom(),
                    request.getPrenom(),
                    request.getEmail(),
                    request.getTelephone(),
                    request.getCin(),
                    request.getAdresse(),
                    request.getSexe(),
                    request.getProfession(),
                    request.getEmployeur(),
                    request.getDateNaissance()
            );
        }

        LocalDateTime now = LocalDateTime.now();

        BigDecimal loyer = safe(request.getLoyerMensuel());
        BigDecimal mensualites = safe(request.getMensualitesCredits());
        BigDecimal autresCharges = safe(request.getAutresChargesFixes());
        BigDecimal chargeEnfants = BigDecimal.valueOf(Math.max(0, safeInt(request.getNombreEnfants()))).multiply(BigDecimal.valueOf(250));
        BigDecimal chargesMensuelles = loyer.add(mensualites).add(autresCharges).add(chargeEnfants);

        BigDecimal revenus = safe(request.getRevenuMensuelNet()).add(safe(request.getAutresRevenusMensuels()));
        BigDecimal revenuAnnuel = safe(request.getRevenuAnnuel());

        int dureeMoisDemandee = Math.max(0, safeInt(request.getDureeMois()));
        BigDecimal montantFinance = safe(request.getMontant());
        BigDecimal mensualiteBnpl = BigDecimal.ZERO;
        if (dureeMoisDemandee > 0 && montantFinance.compareTo(BigDecimal.ZERO) > 0) {
            mensualiteBnpl = montantFinance.divide(
                    BigDecimal.valueOf(dureeMoisDemandee), 4, RoundingMode.HALF_UP);
        }
        // BCT indicateur : seulement les mensualités de crédit (existant + BNPL) / revenus nets mensuels
        BigDecimal totalMensualitesCredits = mensualites.add(mensualiteBnpl);
        BigDecimal tauxEndettement = (revenus.compareTo(BigDecimal.ZERO) > 0)
                ? totalMensualitesCredits.divide(revenus, 4, RoundingMode.HALF_UP)
                : null;

        DossierClient dossier = new DossierClient(
                clientId,
                genererRef("DOS"),
                now,
                now,
                request.getAncienneteEmploiMois(),
                request.getTypeContrat(),
                request.getRevenuMensuelNet(),
                request.getAutresRevenusMensuels(),
                revenuAnnuel,
                request.getEncoursCredits(),
                loyer,
                request.getMensualitesCredits(),
                request.getAutresChargesFixes(),
                chargesMensuelles,
                tauxEndettement,
                request.getSituationFamiliale(),
                request.getNombreEnfants()
        );
        dossier = dossierRepo.save(dossier);

        DemandeFinancement demande = new DemandeFinancement(
                dossier,
                commercantUserId,
                genererRef("DEM"),
                request.getMontant(),
                request.getDureeMois(),
                "EN_ATTENTE_CONSENTEMENT",
                now,
                now,
                request.getTypeProduit()
        );
        demande = demandeRepo.save(demande);

        // Upload MinIO + metadata docs
        if (request.getDocuments() != null) {
            for (var docReq : request.getDocuments()) {
                MultipartFile file = docReq.getFile();
                if (file == null || file.isEmpty()) {
                    throw new IllegalArgumentException("Fichier manquant pour le document: " + docReq.getTypeDocument());
                }

                String filename = file.getOriginalFilename();
                if (filename == null || filename.isBlank()) filename = "document";

                String objectKey = "clients/" + clientId
                        + "/dossiers/" + dossier.getId()
                        + "/" + System.currentTimeMillis() + "-" + filename;

                String contentType = file.getContentType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = "application/octet-stream";
                }

                try (InputStream is = file.getInputStream()) {
                    minioClient.putObject(
                            PutObjectArgs.builder()
                                    .bucket(bucket)
                                    .object(objectKey)
                                    .stream(is, file.getSize(), -1)
                                    .contentType(contentType)
                                    .build()
                    );
                } catch (Exception e) {
                    throw new RuntimeException("Upload MinIO échoué pour " + filename, e);
                }

                DocumentDossier doc = new DocumentDossier(
                        dossier,
                        docReq.getTypeDocument(),
                        objectKey,
                        filename,
                        contentType,
                        file.getSize()
                );
                documentRepo.save(doc);
            }
        }

        // IMPORTANT: créer + envoyer lien consentement (2h)
        String token = actionClientService.createActionLink(
                demande.getId(),
                request.getEmail(),
                TypeActionClient.CONSENTEMENT,
                frontBaseUrl
        );

        return demande;
    }

    @Override
    public DemandeFinancement validerConsentementEtSoumettre(String token) {
        Long demandeId = actionClientService.validateTokenForConsent(token);

        DemandeFinancement demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));

        demande.setStatut("SOUMISE");
        demande.setDateDerniereMiseAJour(LocalDateTime.now());
        return demandeRepo.save(demande);
    }

    @Override
    public List<DemandeFinancement> listerDemandesParClient(Long clientId) {
        Long authId = SecurityUtils.getCurrentUserId();
        if (!authId.equals(clientId)) {
            throw new IllegalStateException("L'id ne correspond pas à l'utilisateur authentifié");
        }
        return demandeRepo.findByCommercantUserId(clientId);
    }

    @Override
    public DemandeFinancement getDemandeParIdPourCommercant(Long demandeId) {
        Long authId = SecurityUtils.getCurrentUserId();
        DemandeFinancement demande = demandeRepo.findCompleteById(demandeId)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable: " + demandeId));
        if (!authId.equals(demande.getCommercantUserId())) {
            throw new IllegalStateException("Cette demande n'appartient pas au commerçant authentifié");
        }
        return demande;
    }

    @Override
    public DernierDossierFinancierResponse getDernierDossierFinancierParCin(String cin) {
        Long clientId = clientRemoteService.getClientIdByCin(cin);

        var dossier = dossierRepo.findTopByClientIdOrderByDateDerniereMiseAJourDesc(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Aucun dossier financier trouvé pour CIN=" + cin));

        return new DernierDossierFinancierResponse(
                dossier.getAncienneteEmploiMois() != null ? dossier.getAncienneteEmploiMois() : 0,
                safe(dossier.getRevenuMensuelNet()),
                safe(dossier.getAutresRevenusMensuels()),
                safe(dossier.getLoyerMensuel()),
                safe(dossier.getMensualitesCredits()),
                safe(dossier.getAutresChargesFixes()),
                safe(dossier.getEncoursCredits())
        );
    }

    @Override
    public String getPresignedDocumentUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(10, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Impossible de générer l'URL MinIO", e);
        }
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }

    private void validateCreationRequest(CreationDemandeCompleteRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Requête manquante.");
        }

        if (isBlank(request.getTypeContrat())) {
            throw new IllegalArgumentException("Le type de contrat est obligatoire.");
        }
        if (!"CDI".equalsIgnoreCase(request.getTypeContrat()) && !"CDD".equalsIgnoreCase(request.getTypeContrat())) {
            throw new IllegalArgumentException("Le type de contrat doit être CDI ou CDD.");
        }

        if (request.getAncienneteEmploiMois() == null || request.getAncienneteEmploiMois() < 0) {
            throw new IllegalArgumentException("L'ancienneté en mois est invalide.");
        }
        if (request.getDateNaissance() == null) {
            throw new IllegalArgumentException("La date de naissance est obligatoire.");
        }

        if (request.getNombreEnfants() != null && request.getNombreEnfants() < 0) {
            throw new IllegalArgumentException("Le nombre d'enfants est invalide.");
        }

        boolean aUnLoyer = safe(request.getLoyerMensuel()).compareTo(BigDecimal.ZERO) > 0;

        validateRequiredDocuments(request.getDocuments(), aUnLoyer, safe(request.getMontant()));
    }

    private void validateRequiredDocuments(List<CreationDemandeCompleteRequest.DocumentMultipart> docs, boolean aUnLoyer, BigDecimal montant) {
        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("Pièces justificatives obligatoires manquantes.");
        }

        Set<String> docTypes = new HashSet<>();
        for (var d : docs) {
            if (d == null || isBlank(d.getTypeDocument()) || d.getFile() == null || d.getFile().isEmpty()) {
                throw new IllegalArgumentException("Document invalide détecté.");
            }
            docTypes.add(d.getTypeDocument().trim().toLowerCase());
        }

        Set<String> required = new HashSet<>(Set.of(
                "cin",
                "fiche_paie_m1",
                "fiche_paie_m2",
                "fiche_paie_m3",
                "attestation_travail"
        ));
        if (aUnLoyer) required.add("justificatif_loyer");
        if (montant.compareTo(BigDecimal.valueOf(10000)) > 0) required.add("devis");

        required.removeAll(docTypes);
        if (!required.isEmpty()) {
            throw new IllegalArgumentException("Pièces obligatoires manquantes: " + String.join(", ", required));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String genererRef(String prefix) {
        return prefix + "-" + System.currentTimeMillis();
    }
}