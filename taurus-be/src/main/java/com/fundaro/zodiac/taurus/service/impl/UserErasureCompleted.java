package com.fundaro.zodiac.taurus.service.impl;

/**
 * GDPR erasure of a user without material to return, completed within the transaction of the tenant: the inventory
 * data that a pending erasure request removes on completion are removed at once.
 */
public record UserErasureCompleted(String userKeycloakId, String tenantCode, String actor) {
}
