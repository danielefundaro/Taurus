package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * A person who has to sign an act, with the role in which they sign.
 */
@Entity
@Table(name = "signature_act_signer")
public class SignatureActSigner extends TenantAuditedEntity {

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signer_role", nullable = false, length = 16)
    private SignerRole signerRole;

    @Column(name = "required", nullable = false)
    private boolean required;

    /**
     * Keycloak identifier of the signer; {@code null} for a parent or guardian without an account.
     */
    @Column(name = "user_keycloak_id")
    private String userKeycloakId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    /**
     * Address of a parent or guardian without an account; signers with an account receive the codes at the e-mail of
     * their account.
     */
    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SignerStatus status;

    @Column(name = "signed_at")
    private ZonedDateTime signedAt;

    public Long getActId() {
        return actId;
    }

    public void setActId(Long actId) {
        this.actId = actId;
    }

    public SignerRole getSignerRole() {
        return signerRole;
    }

    public void setSignerRole(SignerRole signerRole) {
        this.signerRole = signerRole;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getUserKeycloakId() {
        return userKeycloakId;
    }

    public void setUserKeycloakId(String userKeycloakId) {
        this.userKeycloakId = userKeycloakId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public SignerStatus getStatus() {
        return status;
    }

    public void setStatus(SignerStatus status) {
        this.status = status;
    }

    public ZonedDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(ZonedDateTime signedAt) {
        this.signedAt = signedAt;
    }
}
