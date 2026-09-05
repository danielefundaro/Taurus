package com.fundaro.zodiac.taurus.service.notification;

public record PushDeliveryResult(int accepted, int invalid, int temporaryFailures, int permanentFailures, int devices) {
    public static PushDeliveryResult noDevices() { return new PushDeliveryResult(0, 0, 0, 0, 0); }
    public boolean delivered() { return accepted > 0; }
    public boolean retryable() { return !delivered() && temporaryFailures > 0; }
}
