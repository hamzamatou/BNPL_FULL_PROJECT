package tn.uib.bnpl.gestion_demande.classes;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class ActionClientToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // token du lien reçu par email
    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Long demandeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeActionClient typeAction; // CONSENTEMENT, INFO_COMPLEMENTAIRE

    @Column(nullable = false)
    private String emailClient;

    // validité du lien (2h)
    @Column(nullable = false)
    private LocalDateTime tokenExpiresAt;

    // OTP (hashé)
    private String otpHash;
    private LocalDateTime otpExpiresAt;   // 10 min
    private Integer otpAttempts;

    // étapes
    private Boolean otpVerified;
    private Boolean used;

    private LocalDateTime createdAt;
    private LocalDateTime usedAt;

    public ActionClientToken() {
        // JPA
    }

    public ActionClientToken(String token,
                             Long demandeId,
                             TypeActionClient typeAction,
                             String emailClient,
                             LocalDateTime tokenExpiresAt,
                             LocalDateTime createdAt) {
        this.token = token;
        this.demandeId = demandeId;
        this.typeAction = typeAction;
        this.emailClient = emailClient;
        this.tokenExpiresAt = tokenExpiresAt;
        this.createdAt = createdAt;
        this.otpAttempts = 0;
        this.otpVerified = false;
        this.used = false;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getDemandeId() { return demandeId; }
    public TypeActionClient getTypeAction() { return typeAction; }
    public String getEmailClient() { return emailClient; }
    public LocalDateTime getTokenExpiresAt() { return tokenExpiresAt; }
    public String getOtpHash() { return otpHash; }
    public LocalDateTime getOtpExpiresAt() { return otpExpiresAt; }
    public Integer getOtpAttempts() { return otpAttempts; }
    public Boolean getOtpVerified() { return otpVerified; }
    public Boolean getUsed() { return used; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUsedAt() { return usedAt; }

    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public void setOtpExpiresAt(LocalDateTime otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }
    public void setOtpAttempts(Integer otpAttempts) { this.otpAttempts = otpAttempts; }
    public void setOtpVerified(Boolean otpVerified) { this.otpVerified = otpVerified; }
    public void setUsed(Boolean used) { this.used = used; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}