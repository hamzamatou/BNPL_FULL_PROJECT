package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;

@Entity
public class DocumentDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Dossier auquel appartient le document
    @ManyToOne(optional = false)
    private DossierClient dossierClient;

    // Type fonctionnel du document : CNI, JUSTIF_DOMICILE, RELEVE_BANCAIRE, AUTRE...
    @Column(nullable = false)
    private String typeDocument;

    // Clé de l'objet dans MinIO (chemin)
    @Column(nullable = false)
    private String objectKey;

    // Infos pratiques pour le front
    private String nomFichier;
    private String contentType;
    private Long tailleOctets;

    // --- Getters / Setters ---

    public Long getId() {
        return id;
    }

    public DossierClient getDossierClient() {
        return dossierClient;
    }

    public void setDossierClient(DossierClient dossierClient) {
        this.dossierClient = dossierClient;
    }

    public String getTypeDocument() {
        return typeDocument;
    }

    public void setTypeDocument(String typeDocument) {
        this.typeDocument = typeDocument;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getNomFichier() {
        return nomFichier;
    }

    public void setNomFichier(String nomFichier) {
        this.nomFichier = nomFichier;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getTailleOctets() {
        return tailleOctets;
    }

    public void setTailleOctets(Long tailleOctets) {
        this.tailleOctets = tailleOctets;
    }
    public DocumentDossier() {
        // JPA
    }
    public DocumentDossier(DossierClient dossierClient,
            String typeDocument,
            String objectKey,
            String nomFichier,
            String contentType,
            Long tailleOctets) {
this.dossierClient = dossierClient;
this.typeDocument = typeDocument;
this.objectKey = objectKey;
this.nomFichier = nomFichier;
this.contentType = contentType;
this.tailleOctets = tailleOctets;
}
}
