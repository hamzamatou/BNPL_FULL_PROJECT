package tn.uib.bnpl.reporting_archivage.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "acces_plateforme_historique", indexes = {
        @Index(name = "idx_acces_user", columnList = "userId"),
        @Index(name = "idx_acces_date", columnList = "dateAcces"),
        @Index(name = "idx_acces_ip", columnList = "adresseIp")
})
public class AccesPlateformeHistorique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String userEmail;
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeAccesPlateforme typeAcces;

    @Column(nullable = false, length = 500)
    private String description;

    private String adresseIp;
    private String userAgent;
    private String endpoint;
    private String methodeHttp;
    private boolean suspect;

    @Column(columnDefinition = "TEXT")
    private String detailsJson;

    private String correlationId;

    @Column(nullable = false)
    private LocalDateTime dateAcces;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dateEnregistrement;

    public AccesPlateformeHistorique() {
    }

    @PrePersist
    void prePersist() {
        if (dateEnregistrement == null) {
            dateEnregistrement = LocalDateTime.now();
        }
        if (dateAcces == null) {
            dateAcces = dateEnregistrement;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    public TypeAccesPlateforme getTypeAcces() { return typeAcces; }
    public void setTypeAcces(TypeAccesPlateforme typeAcces) { this.typeAcces = typeAcces; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getMethodeHttp() { return methodeHttp; }
    public void setMethodeHttp(String methodeHttp) { this.methodeHttp = methodeHttp; }
    public boolean isSuspect() { return suspect; }
    public void setSuspect(boolean suspect) { this.suspect = suspect; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getDateAcces() { return dateAcces; }
    public void setDateAcces(LocalDateTime dateAcces) { this.dateAcces = dateAcces; }
    public LocalDateTime getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDateTime dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
}
