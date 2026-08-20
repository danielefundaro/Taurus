package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

@MappedSuperclass
public abstract class TenantAuditedEntity extends AuditedEntity {

    @Version
    @jakarta.persistence.Column(name = "entity_version", nullable = false)
    private long entityVersion;

    public long getEntityVersion() { return entityVersion; }
}
