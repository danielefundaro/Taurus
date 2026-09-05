package com.fundaro.zodiac.taurus.service.onboarding;

public final class OnboardingWorkEvents {
    private OnboardingWorkEvents() {}
    public record Validate(Long jobId, byte[] content, String tenantCode) {}
    public record Apply(Long jobId, String tenantCode, String actor) {}
}
