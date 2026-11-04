package com.fundaro.zodiac.taurus.domain.guidedtour;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The guided tour of one role of a person in the tenant. Only step identifiers are stored: the catalog of the steps
 * lives in the frontend.
 */
@Entity
@Table(name = "guided_tour_progress")
public class GuidedTourProgress extends TenantAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private GuidedTourProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private RoleEnum role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GuidedTourStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "seen_steps", nullable = false, columnDefinition = "jsonb")
    private List<String> seenSteps = new ArrayList<>();

    @Column(name = "last_step_id", length = 64)
    private String lastStepId;

    @Column(name = "auto_proposals", nullable = false)
    private int autoProposals;

    @Column(name = "catalog_revision", nullable = false)
    private int catalogRevision;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    public GuidedTourProfile getProfile() {
        return profile;
    }

    public void setProfile(GuidedTourProfile profile) {
        this.profile = profile;
    }

    public RoleEnum getRole() {
        return role;
    }

    public void setRole(RoleEnum role) {
        this.role = role;
    }

    public GuidedTourStatus getStatus() {
        return status;
    }

    public void setStatus(GuidedTourStatus status) {
        this.status = status;
    }

    public List<String> getSeenSteps() {
        return seenSteps;
    }

    public void setSeenSteps(List<String> seenSteps) {
        this.seenSteps = seenSteps;
    }

    public String getLastStepId() {
        return lastStepId;
    }

    public void setLastStepId(String lastStepId) {
        this.lastStepId = lastStepId;
    }

    public int getAutoProposals() {
        return autoProposals;
    }

    public void setAutoProposals(int autoProposals) {
        this.autoProposals = autoProposals;
    }

    public int getCatalogRevision() {
        return catalogRevision;
    }

    public void setCatalogRevision(int catalogRevision) {
        this.catalogRevision = catalogRevision;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
