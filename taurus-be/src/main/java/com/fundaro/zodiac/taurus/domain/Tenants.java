package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Objects;

/**
 * A Users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tenants extends CommonFieldsOpenSearch {

    private String code;

    private String email;

    private String domain;

    private Long maxUsers;

    private Date expireDate;

    private Boolean active;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Long getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Long maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Date getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(Date expireDate) {
        this.expireDate = expireDate;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tenants tenants)) {
            return false;
        }
        return super.equals(tenants) &&
            Objects.equals(code, tenants.code) &&
            Objects.equals(email, tenants.email) &&
            Objects.equals(domain, tenants.domain) &&
            Objects.equals(maxUsers, tenants.maxUsers) &&
            Objects.equals(expireDate, tenants.expireDate) &&
            Objects.equals(active, tenants.active);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, maxUsers, expireDate, active);
    }

    @Override
    public String toString() {
        return "Tenants{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", code='" + getCode() + '\'' +
            ", email='" + getEmail() + '\'' +
            ", domain='" + getDomain() + '\'' +
            ", maxUsers=" + getMaxUsers() +
            ", expireDate=" + getExpireDate() +
            ", active=" + getActive() +
            ", description='" + getDescription() + "'" +
            '}';
    }
}
