package com.fundaro.zodiac.taurus.domain.guidedtour;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The guided tour settings of a person in the tenant. It is looked up by subject, as the notification profile: a
 * super administrator may have no user row in the tenant.
 */
@Entity
@Table(name = "guided_tour_profile")
public class GuidedTourProfile extends TenantAuditedEntity {

    @Column(name = "user_keycloak_id", nullable = false, length = 255)
    private String keycloakSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @Column(name = "auto_start_enabled", nullable = false)
    private boolean autoStartEnabled = true;

    /** Set only by the release migration: the member was already in the tenant and is invited instead. */
    @Column(name = "legacy_invitation", nullable = false)
    private boolean legacyInvitation;

    public String getKeycloakSubject() {
        return keycloakSubject;
    }

    public void setKeycloakSubject(String keycloakSubject) {
        this.keycloakSubject = keycloakSubject;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public boolean isAutoStartEnabled() {
        return autoStartEnabled;
    }

    public void setAutoStartEnabled(boolean autoStartEnabled) {
        this.autoStartEnabled = autoStartEnabled;
    }

    public boolean isLegacyInvitation() {
        return legacyInvitation;
    }

    public void setLegacyInvitation(boolean legacyInvitation) {
        this.legacyInvitation = legacyInvitation;
    }
}
