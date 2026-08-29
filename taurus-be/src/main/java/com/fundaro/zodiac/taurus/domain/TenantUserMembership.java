package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "tenant_user_membership", schema = "public")
public class TenantUserMembership extends AuditFields {
    @EmbeddedId
    private TenantUserMembershipId id;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "joined_at", nullable = false)
    private ZonedDateTime joinedAt;

    @Column(name = "left_at")
    private ZonedDateTime leftAt;

    public TenantUserMembershipId getId() { return id; }
    public void setId(TenantUserMembershipId id) { this.id = id; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public ZonedDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(ZonedDateTime joinedAt) { this.joinedAt = joinedAt; }
    public ZonedDateTime getLeftAt() { return leftAt; }
    public void setLeftAt(ZonedDateTime leftAt) { this.leftAt = leftAt; }
}
