package com.fundaro.zodiac.taurus.domain.eventpreparation;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "calendar_event_material")
public class CalendarEventMaterial extends TenantAuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvents event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private InventoryAssignment assignment;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "required_quantity", nullable = false)
    private int requiredQuantity;
    @Column(name = "notes", length = 2000)
    private String notes;
    @Column(name = "confirmation_hash", length = 64)
    private String confirmationHash;
    @Column(name = "confirmation_at")
    private ZonedDateTime confirmationAt;
    @Column(name = "confirmation_by")
    private String confirmationBy;

    public CalendarEvents getEvent() {
        return event;
    }

    public void setEvent(CalendarEvents value) {
        event = value;
    }

    public InventoryItem getItem() {
        return item;
    }

    public void setItem(InventoryItem value) {
        item = value;
    }

    public InventoryAssignment getAssignment() {
        return assignment;
    }

    public void setAssignment(InventoryAssignment value) {
        assignment = value;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int value) {
        displayOrder = value;
    }

    public int getRequiredQuantity() {
        return requiredQuantity;
    }

    public void setRequiredQuantity(int value) {
        requiredQuantity = value;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String value) {
        notes = value;
    }

    public String getConfirmationHash() {
        return confirmationHash;
    }

    public void setConfirmationHash(String value) {
        confirmationHash = value;
    }

    public ZonedDateTime getConfirmationAt() {
        return confirmationAt;
    }

    public void setConfirmationAt(ZonedDateTime value) {
        confirmationAt = value;
    }

    public String getConfirmationBy() {
        return confirmationBy;
    }

    public void setConfirmationBy(String value) {
        confirmationBy = value;
    }
}
