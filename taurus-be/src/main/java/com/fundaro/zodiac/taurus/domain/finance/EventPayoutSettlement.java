package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * The part of a settlement movement that pays one participant for one event. The shares are always recalculated:
 * only what was paid is stored, because it is a fact and not a calculation.
 */
@Entity
@Table(name = "event_payout_settlement")
public class EventPayoutSettlement extends TenantAuditedEntity {

    @Column(name = "movement_id", nullable = false)
    private Long movementId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "event_name_snapshot")
    private String eventNameSnapshot;

    public Long getMovementId() {
        return movementId;
    }

    public void setMovementId(Long movementId) {
        this.movementId = movementId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getEventNameSnapshot() {
        return eventNameSnapshot;
    }

    public void setEventNameSnapshot(String eventNameSnapshot) {
        this.eventNameSnapshot = eventNameSnapshot;
    }
}
