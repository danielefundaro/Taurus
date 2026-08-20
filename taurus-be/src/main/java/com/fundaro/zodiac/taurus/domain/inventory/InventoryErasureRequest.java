package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_erasure_request")
public class InventoryErasureRequest extends AuditedEntity {
    @Column(name = "user_index", nullable = false) private String userIndex;
    @Column(name = "user_keycloak_id", nullable = false) private String userKeycloakId;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "email") private String email;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 48) private InventoryErasureStatus status;
    @Column(name = "requested_at", nullable = false) private ZonedDateTime requestedAt;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "resolved_at") private ZonedDateTime resolvedAt;
    @Column(name = "resolved_by") private String resolvedBy;

    public String getUserIndex() { return userIndex; }
    public void setUserIndex(String userIndex) { this.userIndex = userIndex; }
    public String getUserKeycloakId() { return userKeycloakId; }
    public void setUserKeycloakId(String userKeycloakId) { this.userKeycloakId = userKeycloakId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public InventoryErasureStatus getStatus() { return status; }
    public void setStatus(InventoryErasureStatus status) { this.status = status; }
    public ZonedDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(ZonedDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public ZonedDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(ZonedDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
