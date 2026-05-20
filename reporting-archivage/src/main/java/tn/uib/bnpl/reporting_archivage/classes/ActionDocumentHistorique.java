package tn.uib.bnpl.reporting_archivage.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "action_document_historique", indexes = {
        @Index(name = "idx_action_doc_demande", columnList = "demandeId"),
        @Index(name = "idx_action_doc_object", columnList = "objectKey")
})
public class ActionDocumentHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long demandeId;

    private String referenceDemande;
    private Long documentId;
    private String objectKey;
    private String typeDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TypeActionDocument typeAction;

    @Column(nullable = false, length = 500)
    private String libelle;

    private Long acteurUserId;
    private String acteurEmail;
    private String acteurRole;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime dateAction;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnregistrement;

    public ActionDocumentHistorique() {
    }

    @PrePersist
    void prePersist() {
        if (dateEnregistrement == null) {
            dateEnregistrement = LocalDateTime.now();
        }
        if (dateAction == null) {
            dateAction = dateEnregistrement;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDemandeId() { return demandeId; }
    public void setDemandeId(Long demandeId) { this.demandeId = demandeId; }
    public String getReferenceDemande() { return referenceDemande; }
    public void setReferenceDemande(String referenceDemande) { this.referenceDemande = referenceDemande; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getTypeDocument() { return typeDocument; }
    public void setTypeDocument(String typeDocument) { this.typeDocument = typeDocument; }
    public TypeActionDocument getTypeAction() { return typeAction; }
    public void setTypeAction(TypeActionDocument typeAction) { this.typeAction = typeAction; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public Long getActeurUserId() { return acteurUserId; }
    public void setActeurUserId(Long acteurUserId) { this.acteurUserId = acteurUserId; }
    public String getActeurEmail() { return acteurEmail; }
    public void setActeurEmail(String acteurEmail) { this.acteurEmail = acteurEmail; }
    public String getActeurRole() { return acteurRole; }
    public void setActeurRole(String acteurRole) { this.acteurRole = acteurRole; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getDateAction() { return dateAction; }
    public void setDateAction(LocalDateTime dateAction) { this.dateAction = dateAction; }
    public LocalDateTime getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDateTime dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
}
