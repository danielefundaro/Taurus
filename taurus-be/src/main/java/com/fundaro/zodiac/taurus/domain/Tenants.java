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

    private String address;
    private String postalCode;
    private String city;
    private String province;
    private String country;
    private String taxCode;
    private String vatNumber;
    private String logoUrl;

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

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getTaxCode() { return taxCode; }
    public void setTaxCode(String taxCode) { this.taxCode = taxCode; }
    public String getVatNumber() { return vatNumber; }
    public void setVatNumber(String vatNumber) { this.vatNumber = vatNumber; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

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
            Objects.equals(active, tenants.active) &&
            Objects.equals(address, tenants.address) &&
            Objects.equals(postalCode, tenants.postalCode) &&
            Objects.equals(city, tenants.city) &&
            Objects.equals(province, tenants.province) &&
            Objects.equals(country, tenants.country) &&
            Objects.equals(taxCode, tenants.taxCode) &&
            Objects.equals(vatNumber, tenants.vatNumber) &&
            Objects.equals(logoUrl, tenants.logoUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, maxUsers, expireDate, active, address, postalCode, city, province, country, taxCode, vatNumber, logoUrl);
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
            ", address='" + getAddress() + '\'' +
            ", postalCode='" + getPostalCode() + '\'' +
            ", city='" + getCity() + '\'' +
            ", province='" + getProvince() + '\'' +
            ", country='" + getCountry() + '\'' +
            ", taxCode='" + getTaxCode() + '\'' +
            ", vatNumber='" + getVatNumber() + '\'' +
            ", logoUrl='" + getLogoUrl() + '\'' +
            ", description='" + getDescription() + "'" +
            '}';
    }
}
