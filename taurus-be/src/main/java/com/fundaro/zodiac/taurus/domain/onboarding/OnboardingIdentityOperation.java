package com.fundaro.zodiac.taurus.domain.onboarding;

import jakarta.persistence.*;

@Entity @Table(name = "onboarding_identity_operation")
public class OnboardingIdentityOperation {
    public enum Type { CREATE, LINK_EXISTING }
    public enum Status { PLANNED, APPLIED, COMPENSATED, COMPENSATION_FAILED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "job_id", nullable = false) private OnboardingImportJob job;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "row_id", nullable = false) private OnboardingImportRow row;
    @Enumerated(EnumType.STRING) @Column(name = "operation_type", nullable = false, length = 32) private Type operationType;
    @Column(name = "keycloak_id") private String keycloakId;
    @Column(name = "created_by_job", nullable = false) private boolean createdByJob;
    @Column(name = "previously_in_group", nullable = false) private boolean previouslyInGroup;
    @Column(name = "previous_roles", columnDefinition = "text") private String previousRoles;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private Status status = Status.PLANNED;
    @Column(name = "last_error_code", length = 128) private String lastErrorCode;
    public Long getId() { return id; }
    public OnboardingImportJob getJob() { return job; } public void setJob(OnboardingImportJob v) { job = v; }
    public OnboardingImportRow getRow() { return row; } public void setRow(OnboardingImportRow v) { row = v; }
    public Type getOperationType() { return operationType; } public void setOperationType(Type v) { operationType = v; }
    public String getKeycloakId() { return keycloakId; } public void setKeycloakId(String v) { keycloakId = v; }
    public boolean isCreatedByJob() { return createdByJob; } public void setCreatedByJob(boolean v) { createdByJob = v; }
    public boolean isPreviouslyInGroup() { return previouslyInGroup; } public void setPreviouslyInGroup(boolean v) { previouslyInGroup = v; }
    public String getPreviousRoles() { return previousRoles; } public void setPreviousRoles(String v) { previousRoles = v; }
    public Status getStatus() { return status; } public void setStatus(Status v) { status = v; }
    public String getLastErrorCode() { return lastErrorCode; } public void setLastErrorCode(String v) { lastErrorCode = v; }
}
