package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TenantUserMembershipId implements Serializable {
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "user_identity_id")
    private Long userIdentityId;

    public TenantUserMembershipId() {}

    public TenantUserMembershipId(Long tenantId, Long userIdentityId) {
        this.tenantId = tenantId;
        this.userIdentityId = userIdentityId;
    }

    public Long getTenantId() { return tenantId; }
    public Long getUserIdentityId() { return userIdentityId; }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof TenantUserMembershipId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(userIdentityId, that.userIdentityId);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, userIdentityId); }
}
