package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "accounting_year")
public class AccountingYear extends TenantAuditedEntity {

    @Column(name = "year", nullable = false, unique = true)
    private int year;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountingYearStatus status = AccountingYearStatus.OPEN;

    @Column(name = "rolled_over_at")
    private ZonedDateTime rolledOverAt;

    @Column(name = "rolled_over_by")
    private String rolledOverBy;

    @Column(name = "last_recalculated_at")
    private ZonedDateTime lastRecalculatedAt;

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public AccountingYearStatus getStatus() { return status; }
    public void setStatus(AccountingYearStatus status) { this.status = status; }
    public ZonedDateTime getRolledOverAt() { return rolledOverAt; }
    public void setRolledOverAt(ZonedDateTime rolledOverAt) { this.rolledOverAt = rolledOverAt; }
    public String getRolledOverBy() { return rolledOverBy; }
    public void setRolledOverBy(String rolledOverBy) { this.rolledOverBy = rolledOverBy; }
    public ZonedDateTime getLastRecalculatedAt() { return lastRecalculatedAt; }
    public void setLastRecalculatedAt(ZonedDateTime lastRecalculatedAt) { this.lastRecalculatedAt = lastRecalculatedAt; }
}
