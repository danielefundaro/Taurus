package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserTenantsDTO implements Serializable {

    private ChildrenEntitiesDTO tenant;

    private Set<RoleEnum> roles;

    public ChildrenEntitiesDTO getTenant() {
        return tenant;
    }

    public void setTenant(ChildrenEntitiesDTO tenant) {
        this.tenant = tenant;
    }

    public Set<RoleEnum> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEnum> roles) {
        this.roles = roles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserTenantsDTO userTenantsDTO)) {
            return false;
        }

        return super.equals(userTenantsDTO) &&
            Objects.equals(tenant, userTenantsDTO.tenant) &&
            Objects.equals(roles, userTenantsDTO.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenant, roles);
    }

    @Override
    public String toString() {
        return "UserTenantsDTO{" +
            "tenant=" + getTenant() +
            ", roles=" + getRoles() +
            '}';
    }
}
