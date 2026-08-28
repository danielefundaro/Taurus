package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_assignment")
public class InventoryAssignment extends TenantAuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    @Column(name = "user_index", nullable = false)
    private Long userIndex;
    @Column(name = "user_keycloak_id", nullable = false, length = 255)
    private String userKeycloakId;
    @Column(name = "user_name", nullable = false)
    private String userName;
    @Column(name = "user_last_name", nullable = false)
    private String userLastName;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "assigned_quantity", nullable = false)
    private int assignedQuantity;
    @Column(name = "returned_quantity", nullable = false)
    private int returnedQuantity;
    @Column(name = "assigned_at", nullable = false)
    private ZonedDateTime assignedAt;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InventoryAssignmentStatus status;
    @Column(name = "current_revision", nullable = false)
    private int currentRevision;

    public InventoryItem getItem() { return item; }
    public void setItem(InventoryItem item) { this.item = item; }
    public Long getUserIndex() { return userIndex; }
    public void setUserIndex(Long userIndex) { this.userIndex = userIndex; }
    public String getUserKeycloakId() { return userKeycloakId; }
    public void setUserKeycloakId(String userKeycloakId) { this.userKeycloakId = userKeycloakId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserLastName() { return userLastName; }
    public void setUserLastName(String userLastName) { this.userLastName = userLastName; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public int getAssignedQuantity() { return assignedQuantity; }
    public void setAssignedQuantity(int assignedQuantity) { this.assignedQuantity = assignedQuantity; }
    public int getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(int returnedQuantity) { this.returnedQuantity = returnedQuantity; }
    public ZonedDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(ZonedDateTime assignedAt) { this.assignedAt = assignedAt; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public InventoryAssignmentStatus getStatus() { return status; }
    public void setStatus(InventoryAssignmentStatus status) { this.status = status; }
    public int getCurrentRevision() { return currentRevision; }
    public void setCurrentRevision(int currentRevision) { this.currentRevision = currentRevision; }
    public int getOutstandingQuantity() { return assignedQuantity - returnedQuantity; }
}
