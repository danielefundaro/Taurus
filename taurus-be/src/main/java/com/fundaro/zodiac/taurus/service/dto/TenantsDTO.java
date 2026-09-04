package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.Date;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TenantsDTO extends CommonFieldsOpenSearchDTO {

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

    @Pattern(regexp = "[A-Z]{2}", message = "must be a two-letter uppercase country code")
    private String country;
    private String taxCode;
    private String vatNumber;

    @URL(regexp = "https?://.*", message = "must be a valid HTTP or HTTPS URL")
    @Size(max = 2048)
    private String logoUrl;

    @NotBlank
    @Size(max = 64)
    private String timeZone = "Europe/Rome";

    private Boolean financeEnabled = true;
    private Boolean inventoryEnabled = true;
    private Long entityVersion;

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
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public Boolean getFinanceEnabled() { return financeEnabled; }
    public void setFinanceEnabled(Boolean financeEnabled) { this.financeEnabled = financeEnabled; }
    public Boolean getInventoryEnabled() { return inventoryEnabled; }
    public void setInventoryEnabled(Boolean inventoryEnabled) { this.inventoryEnabled = inventoryEnabled; }
    public Long getEntityVersion() { return entityVersion; }
    public void setEntityVersion(Long entityVersion) { this.entityVersion = entityVersion; }

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
            Objects.equals(maxUsers, that.maxUsers) &&
            Objects.equals(expireDate, that.expireDate) &&
            Objects.equals(active, that.active) &&
            Objects.equals(address, that.address) &&
            Objects.equals(postalCode, that.postalCode) &&
            Objects.equals(city, that.city) &&
            Objects.equals(province, that.province) &&
            Objects.equals(country, that.country) &&
            Objects.equals(taxCode, that.taxCode) &&
            Objects.equals(vatNumber, that.vatNumber) &&
            Objects.equals(logoUrl, that.logoUrl) &&
            Objects.equals(timeZone, that.timeZone) &&
            Objects.equals(financeEnabled, that.financeEnabled) &&
            Objects.equals(inventoryEnabled, that.inventoryEnabled) &&
            Objects.equals(entityVersion, that.entityVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, maxUsers, expireDate, active, address, postalCode, city, province, country, taxCode, vatNumber, logoUrl, timeZone, financeEnabled, inventoryEnabled, entityVersion);
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
            ", maxUsers=" + maxUsers +
            ", expireDate=" + expireDate +
            ", active=" + active +
            ", address='" + address + '\'' +
            ", postalCode='" + postalCode + '\'' +
            ", city='" + city + '\'' +
            ", province='" + province + '\'' +
            ", country='" + country + '\'' +
            ", taxCode='" + taxCode + '\'' +
            ", vatNumber='" + vatNumber + '\'' +
            ", logoUrl='" + logoUrl + '\'' +
            ", timeZone='" + timeZone + '\'' +
            ", financeEnabled=" + financeEnabled +
            ", inventoryEnabled=" + inventoryEnabled +
            ", entityVersion=" + entityVersion +
            '}';
    }
}
