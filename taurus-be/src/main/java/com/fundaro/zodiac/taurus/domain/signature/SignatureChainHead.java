package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

/**
 * Last evidence of the tenant chain, in a single row created by the migration and locked by every new evidence.
 */
@Entity
@Table(name = "signature_chain_head")
public class SignatureChainHead extends TenantAuditedEntity {

    @Column(name = "last_sequence", nullable = false)
    private long lastSequence;

    @Column(name = "last_hash", nullable = false, length = 64)
    private String lastHash;

    public long getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(long lastSequence) {
        this.lastSequence = lastSequence;
    }

    public String getLastHash() {
        return lastHash;
    }

    public void setLastHash(String lastHash) {
        this.lastHash = lastHash;
    }
}
