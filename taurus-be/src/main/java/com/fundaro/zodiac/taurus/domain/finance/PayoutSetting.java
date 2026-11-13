package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A dated version of the parameters of the shares: it applies to the events from its date until the next version.
 */
@Entity
@Table(name = "payout_setting")
public class PayoutSetting extends TenantAuditedEntity {

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "association_share_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal associationSharePercent;

    @Column(name = "rounding_step", nullable = false, precision = 19, scale = 4)
    private BigDecimal roundingStep;

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public BigDecimal getAssociationSharePercent() {
        return associationSharePercent;
    }

    public void setAssociationSharePercent(BigDecimal associationSharePercent) {
        this.associationSharePercent = associationSharePercent;
    }

    public BigDecimal getRoundingStep() {
        return roundingStep;
    }

    public void setRoundingStep(BigDecimal roundingStep) {
        this.roundingStep = roundingStep;
    }
}
