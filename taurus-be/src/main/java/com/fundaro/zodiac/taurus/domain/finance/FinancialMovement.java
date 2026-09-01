package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "financial_movement")
public class FinancialMovement extends TenantAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_year_id", nullable = false)
    private AccountingYear accountingYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private FinancialCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private CalendarEvents event;

    @Column(name = "event_name_snapshot")
    private String eventNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private FinancialDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature", nullable = false, length = 16)
    private FinancialMovementNature nature = FinancialMovementNature.ORDINARY;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "value_date")
    private LocalDate valueDate;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "counterparty", length = 500)
    private String counterparty;

    @Column(name = "document_reference")
    private String documentReference;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "transfer_group")
    private UUID transferGroup;

    @Column(name = "reconciled", nullable = false)
    private boolean reconciled;

    @Column(name = "reconciled_at")
    private ZonedDateTime reconciledAt;

    @Column(name = "reconciled_by")
    private String reconciledBy;

    @Column(name = "reconciliation_reference")
    private String reconciliationReference;

    @Column(name = "request_key", unique = true)
    private UUID requestKey;

    public AccountingYear getAccountingYear() { return accountingYear; }
    public void setAccountingYear(AccountingYear accountingYear) { this.accountingYear = accountingYear; }
    public FinancialAccount getAccount() { return account; }
    public void setAccount(FinancialAccount account) { this.account = account; }
    public FinancialCategory getCategory() { return category; }
    public void setCategory(FinancialCategory category) { this.category = category; }
    public CalendarEvents getEvent() { return event; }
    public void setEvent(CalendarEvents event) { this.event = event; }
    public String getEventNameSnapshot() { return eventNameSnapshot; }
    public void setEventNameSnapshot(String eventNameSnapshot) { this.eventNameSnapshot = eventNameSnapshot; }
    public FinancialDirection getDirection() { return direction; }
    public void setDirection(FinancialDirection direction) { this.direction = direction; }
    public FinancialMovementNature getNature() { return nature; }
    public void setNature(FinancialMovementNature nature) { this.nature = nature; }
    public LocalDate getBookingDate() { return bookingDate; }
    public void setBookingDate(LocalDate bookingDate) { this.bookingDate = bookingDate; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String counterparty) { this.counterparty = counterparty; }
    public String getDocumentReference() { return documentReference; }
    public void setDocumentReference(String documentReference) { this.documentReference = documentReference; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getTransferGroup() { return transferGroup; }
    public void setTransferGroup(UUID transferGroup) { this.transferGroup = transferGroup; }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }
    public ZonedDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(ZonedDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public String getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(String reconciledBy) { this.reconciledBy = reconciledBy; }
    public String getReconciliationReference() { return reconciliationReference; }
    public void setReconciliationReference(String reconciliationReference) { this.reconciliationReference = reconciliationReference; }
    public UUID getRequestKey() { return requestKey; }
    public void setRequestKey(UUID requestKey) { this.requestKey = requestKey; }
}
