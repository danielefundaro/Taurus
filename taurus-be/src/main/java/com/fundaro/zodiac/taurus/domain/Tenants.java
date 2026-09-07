package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Date;
import java.util.Objects;

/**
 * A Users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "tenant", schema = "public")
public class Tenants extends CommonFieldsOpenSearch {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "email")
    private String email;

    @Column(name = "domain")
    private String domain;

    @Column(name = "max_users")
    private Long maxUsers;

    @Column(name = "expire_date")
    private Date expireDate;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "address", length = 500)
    private String address;
    @Column(name = "postal_code")
    private String postalCode;
    @Column(name = "city")
    private String city;
    @Column(name = "province")
    private String province;
    @Column(name = "country", length = 2)
    private String country;
    @Column(name = "tax_code", length = 32)
    private String taxCode;
    @Column(name = "vat_number", length = 32)
    private String vatNumber;
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Europe/Rome";

    @Column(name = "finance_enabled", nullable = false)
    private Boolean financeEnabled = false;

    @Column(name = "inventory_enabled", nullable = false)
    private Boolean inventoryEnabled = false;

    @Column(name = "onboarding_import_enabled", nullable = false)
    private Boolean onboardingImportEnabled = false;

    @Column(name = "external_calendar_feed_enabled", nullable = false)
    private Boolean externalCalendarFeedEnabled = false;

    @Column(name = "inventory_qr_enabled", nullable = false)
    private Boolean inventoryQrEnabled = false;

    @Column(name = "notification_preferences_enabled", nullable = false)
    private Boolean notificationPreferencesEnabled = false;

    @Column(name = "web_push_reminders_enabled", nullable = false)
    private Boolean webPushRemindersEnabled = false;

    @Column(name = "event_preparation_enabled", nullable = false)
    private Boolean eventPreparationEnabled = false;

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
    public Boolean getOnboardingImportEnabled() { return onboardingImportEnabled; }
    public void setOnboardingImportEnabled(Boolean value) { onboardingImportEnabled = value; }
    public Boolean getExternalCalendarFeedEnabled() { return externalCalendarFeedEnabled; }
    public void setExternalCalendarFeedEnabled(Boolean value) { externalCalendarFeedEnabled = value; }
    public Boolean getInventoryQrEnabled() { return inventoryQrEnabled; }
    public void setInventoryQrEnabled(Boolean value) { inventoryQrEnabled = value; }
    public Boolean getNotificationPreferencesEnabled() { return notificationPreferencesEnabled; }
    public void setNotificationPreferencesEnabled(Boolean value) { notificationPreferencesEnabled = value; }
    public Boolean getWebPushRemindersEnabled() { return webPushRemindersEnabled; }
    public void setWebPushRemindersEnabled(Boolean value) { webPushRemindersEnabled = value; }
    public Boolean getEventPreparationEnabled() { return eventPreparationEnabled; }
    public void setEventPreparationEnabled(Boolean value) { eventPreparationEnabled = value; }

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
            Objects.equals(logoUrl, tenants.logoUrl) &&
            Objects.equals(timeZone, tenants.timeZone) &&
            Objects.equals(financeEnabled, tenants.financeEnabled) &&
            Objects.equals(inventoryEnabled, tenants.inventoryEnabled) &&
            Objects.equals(onboardingImportEnabled, tenants.onboardingImportEnabled) &&
            Objects.equals(externalCalendarFeedEnabled, tenants.externalCalendarFeedEnabled) &&
            Objects.equals(inventoryQrEnabled, tenants.inventoryQrEnabled) &&
            Objects.equals(notificationPreferencesEnabled, tenants.notificationPreferencesEnabled) &&
            Objects.equals(webPushRemindersEnabled, tenants.webPushRemindersEnabled) &&
            Objects.equals(eventPreparationEnabled, tenants.eventPreparationEnabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, maxUsers, expireDate, active, address, postalCode, city, province, country, taxCode, vatNumber, logoUrl, timeZone, financeEnabled, inventoryEnabled, onboardingImportEnabled, externalCalendarFeedEnabled, inventoryQrEnabled, notificationPreferencesEnabled, webPushRemindersEnabled, eventPreparationEnabled);
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
            ", timeZone='" + getTimeZone() + '\'' +
            ", financeEnabled=" + getFinanceEnabled() +
            ", inventoryEnabled=" + getInventoryEnabled() +
            ", onboardingImportEnabled=" + getOnboardingImportEnabled() +
            ", externalCalendarFeedEnabled=" + getExternalCalendarFeedEnabled() +
            ", inventoryQrEnabled=" + getInventoryQrEnabled() +
            ", notificationPreferencesEnabled=" + getNotificationPreferencesEnabled() +
            ", webPushRemindersEnabled=" + getWebPushRemindersEnabled() +
            ", eventPreparationEnabled=" + getEventPreparationEnabled() +
            ", description='" + getDescription() + "'" +
            '}';
    }
}
