package tn.uib.bnpl.reporting_archivage.classes;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "dossier_archive", indexes = {
        @Index(name = "idx_archive_demande", columnList = "demandeId", unique = true),
        @Index(name = "idx_archive_date", columnList = "dateArchivage")
})
public class DossierArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long demandeId;

    @Column(nullable = false)
    private String referenceDemande;

    private Long clientId;
    private String cinClient;

    @Column(nullable = false)
    private String statutFinal;

    private BigDecimal montant;
    private Integer dureeMois;
    private String typeProduit;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String snapshotJson;

    @Column(columnDefinition = "TEXT")
    private String documentsMetadataJson;

    private Long archiveParUserId;
    private String archiveParEmail;

    @Column(nullable = false)
    private LocalDateTime dateCloture;

    @Column(nullable = false)
    private LocalDateTime dateArchivage;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnregistrement;

    public DossierArchive() {
    }

    @PrePersist
    void prePersist() {
        if (dateEnregistrement == null) {
            dateEnregistrement = LocalDateTime.now();
        }
        if (dateArchivage == null) {
            dateArchivage = dateEnregistrement;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }
    public String getReferenceDemande() { return referenceDemande; }
    public void setReferenceDemande(String referenceDemande) { this.referenceDemande = referenceDemande; }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
    public String getCinClient() { return cinClient; }
    public void setCinClient(String cinClient) { this.cinClient = cinClient; }
    public String getStatutFinal() { return statutFinal; }
    public void setStatutFinal(String statutFinal) { this.statutFinal = statutFinal; }
    public BigDecimal getMontant() { return montant; }
    public void setMontant(BigDecimal montant) { this.montant = montant; }
    public Integer getDureeMois() { return dureeMois; }
    public void setDureeMois(Integer dureeMois) { this.dureeMois = dureeMois; }
    public String getTypeProduit() { return typeProduit; }
    public void setTypeProduit(String typeProduit) { this.typeProduit = typeProduit; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getDocumentsMetadataJson() { return documentsMetadataJson; }
    public void setDocumentsMetadataJson(String documentsMetadataJson) { this.documentsMetadataJson = documentsMetadataJson; }
    public Long getArchiveParUserId() { return archiveParUserId; }
    public void setArchiveParUserId(Long archiveParUserId) { this.archiveParUserId = archiveParUserId; }
    public String getArchiveParEmail() { return archiveParEmail; }
    public void setArchiveParEmail(String archiveParEmail) { this.archiveParEmail = archiveParEmail; }
    public LocalDateTime getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDateTime dateCloture) { this.dateCloture = dateCloture; }
    public LocalDateTime getDateArchivage() { return dateArchivage; }
    public void setDateArchivage(LocalDateTime dateArchivage) { this.dateArchivage = dateArchivage; }
    public LocalDateTime getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDateTime dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
}
