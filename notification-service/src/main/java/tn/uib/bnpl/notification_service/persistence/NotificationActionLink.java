package tn.uib.bnpl.notification_service.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_action_link", indexes = {
        @Index(name = "idx_action_link_token", columnList = "token", unique = true)
})
public class NotificationActionLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    /** demandeId ou userId selon le type de lien. */
    @Column(nullable = false)
    private Long subjectRef;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionLinkType linkType;

    private String referenceLabel;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    private LocalDateTime usedAt;

    @Column(nullable = false)
    private boolean otpVerified;

    @Column(columnDefinition = "text")
    private String metadataJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getSubjectRef() { return subjectRef; }
    public String getEmail() { return email; }
    public ActionLinkType getLinkType() { return linkType; }
    public String getReferenceLabel() { return referenceLabel; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isUsed() { return used; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public boolean isOtpVerified() { return otpVerified; }
    public String getMetadataJson() { return metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setToken(String token) { this.token = token; }
    public void setSubjectRef(Long subjectRef) { this.subjectRef = subjectRef; }
    public void setEmail(String email) { this.email = email; }
    public void setLinkType(ActionLinkType linkType) { this.linkType = linkType; }
    public void setReferenceLabel(String referenceLabel) { this.referenceLabel = referenceLabel; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setUsed(boolean used) { this.used = used; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
    public void setOtpVerified(boolean otpVerified) { this.otpVerified = otpVerified; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
