package com.fundaro.zodiac.taurus.domain.eventpreparation;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "calendar_event_preparation")
public class CalendarEventPreparation extends TenantAuditedEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private CalendarEvents event;
    @Enumerated(EnumType.STRING) @Column(name = "profile", nullable = false, length = 32)
    private PreparationProfile profile;
    @Column(name = "location_required", nullable = false) private boolean locationRequired;
    @Column(name = "program_required", nullable = false) private boolean programRequired;
    @Column(name = "scores_required", nullable = false) private boolean scoresRequired;
    @Column(name = "availability_required", nullable = false) private boolean availabilityRequired;
    @Column(name = "minimum_available_participants") private Integer minimumAvailableParticipants;
    @Column(name = "availability_deadline_minutes", nullable = false) private int availabilityDeadlineMinutes = 1440;
    @Column(name = "materials_required", nullable = false) private boolean materialsRequired;
    @Column(name = "materials_deadline_minutes", nullable = false) private int materialsDeadlineMinutes = 180;
    @Column(name = "budget_required", nullable = false) private boolean budgetRequired;
    @Column(name = "presence_closure_required", nullable = false) private boolean presenceClosureRequired;
    @Column(name = "financial_closure_required", nullable = false) private boolean financialClosureRequired;
    @Column(name = "budget_confirmation_hash", length = 64) private String budgetConfirmationHash;
    @Column(name = "budget_confirmation_at") private ZonedDateTime budgetConfirmationAt;
    @Column(name = "budget_confirmation_by") private String budgetConfirmationBy;
    @Column(name = "presence_confirmation_hash", length = 64) private String presenceConfirmationHash;
    @Column(name = "presence_confirmation_at") private ZonedDateTime presenceConfirmationAt;
    @Column(name = "presence_confirmation_by") private String presenceConfirmationBy;
    @Column(name = "no_movements_confirmation_hash", length = 64) private String noMovementsConfirmationHash;
    @Column(name = "no_movements_confirmation_at") private ZonedDateTime noMovementsConfirmationAt;
    @Column(name = "no_movements_confirmation_by") private String noMovementsConfirmationBy;

    public CalendarEvents getEvent() { return event; }
    public void setEvent(CalendarEvents event) { this.event = event; }
    public PreparationProfile getProfile() { return profile; }
    public void setProfile(PreparationProfile profile) { this.profile = profile; }
    public boolean isLocationRequired() { return locationRequired; }
    public void setLocationRequired(boolean value) { locationRequired = value; }
    public boolean isProgramRequired() { return programRequired; }
    public void setProgramRequired(boolean value) { programRequired = value; }
    public boolean isScoresRequired() { return scoresRequired; }
    public void setScoresRequired(boolean value) { scoresRequired = value; }
    public boolean isAvailabilityRequired() { return availabilityRequired; }
    public void setAvailabilityRequired(boolean value) { availabilityRequired = value; }
    public Integer getMinimumAvailableParticipants() { return minimumAvailableParticipants; }
    public void setMinimumAvailableParticipants(Integer value) { minimumAvailableParticipants = value; }
    public int getAvailabilityDeadlineMinutes() { return availabilityDeadlineMinutes; }
    public void setAvailabilityDeadlineMinutes(int value) { availabilityDeadlineMinutes = value; }
    public boolean isMaterialsRequired() { return materialsRequired; }
    public void setMaterialsRequired(boolean value) { materialsRequired = value; }
    public int getMaterialsDeadlineMinutes() { return materialsDeadlineMinutes; }
    public void setMaterialsDeadlineMinutes(int value) { materialsDeadlineMinutes = value; }
    public boolean isBudgetRequired() { return budgetRequired; }
    public void setBudgetRequired(boolean value) { budgetRequired = value; }
    public boolean isPresenceClosureRequired() { return presenceClosureRequired; }
    public void setPresenceClosureRequired(boolean value) { presenceClosureRequired = value; }
    public boolean isFinancialClosureRequired() { return financialClosureRequired; }
    public void setFinancialClosureRequired(boolean value) { financialClosureRequired = value; }
    public String getBudgetConfirmationHash() { return budgetConfirmationHash; }
    public void setBudgetConfirmationHash(String value) { budgetConfirmationHash = value; }
    public ZonedDateTime getBudgetConfirmationAt() { return budgetConfirmationAt; }
    public void setBudgetConfirmationAt(ZonedDateTime value) { budgetConfirmationAt = value; }
    public String getBudgetConfirmationBy() { return budgetConfirmationBy; }
    public void setBudgetConfirmationBy(String value) { budgetConfirmationBy = value; }
    public String getPresenceConfirmationHash() { return presenceConfirmationHash; }
    public void setPresenceConfirmationHash(String value) { presenceConfirmationHash = value; }
    public ZonedDateTime getPresenceConfirmationAt() { return presenceConfirmationAt; }
    public void setPresenceConfirmationAt(ZonedDateTime value) { presenceConfirmationAt = value; }
    public String getPresenceConfirmationBy() { return presenceConfirmationBy; }
    public void setPresenceConfirmationBy(String value) { presenceConfirmationBy = value; }
    public String getNoMovementsConfirmationHash() { return noMovementsConfirmationHash; }
    public void setNoMovementsConfirmationHash(String value) { noMovementsConfirmationHash = value; }
    public ZonedDateTime getNoMovementsConfirmationAt() { return noMovementsConfirmationAt; }
    public void setNoMovementsConfirmationAt(ZonedDateTime value) { noMovementsConfirmationAt = value; }
    public String getNoMovementsConfirmationBy() { return noMovementsConfirmationBy; }
    public void setNoMovementsConfirmationBy(String value) { noMovementsConfirmationBy = value; }
}
