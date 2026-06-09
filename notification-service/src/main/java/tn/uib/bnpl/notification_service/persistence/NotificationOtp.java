package tn.uib.bnpl.notification_service.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_otp", indexes = {
        @Index(name = "idx_otp_email_context", columnList = "email,context"),
        @Index(name = "idx_otp_link_token", columnList = "linkToken")
})
public class NotificationOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtpContext context;

    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private int attempts;

    /** Token du lien action client (OTP demande). */
    private String linkToken;

    @Column(columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public OtpContext getContext() { return context; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public int getAttempts() { return attempts; }
    public String getLinkToken() { return linkToken; }
    public String getMetadataJson() { return metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setEmail(String email) { this.email = email; }
    public void setContext(OtpContext context) { this.context = context; }
    public void setCodeHash(String codeHash) { this.codeHash = codeHash; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setUsed(boolean used) { this.used = used; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setLinkToken(String linkToken) { this.linkToken = linkToken; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
