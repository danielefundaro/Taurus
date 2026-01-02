package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantsDTO extends CommonFieldsOpenSearchDTO {

    private String code;

    private String email;

    private String domain;

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
        if (!(o instanceof TenantsDTO that)) {
            return false;
        }
        return super.equals(that) &&
            Objects.equals(code, that.code) &&
            Objects.equals(email, that.email) &&
            Objects.equals(domain, that.domain) &&
            Objects.equals(active, that.active);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, active);
    }

    @Override
    public String toString() {
        return "TenantsDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", code='" + code + '\'' +
            ", email='" + email + '\'' +
            ", domain='" + domain + '\'' +
            ", active=" + active +
            '}';
    }
}
