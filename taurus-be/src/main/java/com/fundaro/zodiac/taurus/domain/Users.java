package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import org.hibernate.annotations.SoftDelete;

import java.util.Date;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "app_user")
public class Users extends CommonFieldsOpenSearch {

    @JsonProperty("last_name")
    @Column(name = "last_name")
    private String lastName;

    @JsonProperty("birth_date")
    @Column(name = "birth_date")
    @Temporal(TemporalType.DATE)
    private Date birthDate;

    @Column(name = "email")
    private String email;

    @Transient
    private Set<ChildrenEntities> tenants;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    @SoftDelete(columnName = "deleted")
    private Set<RoleEnum> roles = new LinkedHashSet<>();

    @Column(name = "active", nullable = false)
    private Boolean active;

    @ManyToMany
    @JoinTable(name = "user_instrument", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "instrument_id"))
    @OrderColumn(name = "display_order")
    @SoftDelete(columnName = "deleted")
    private List<Instruments> instruments = new ArrayList<>();

    @JsonProperty("keycloak_id")
    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_identity_id", nullable = false, unique = true)
    private UserIdentity userIdentity;

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<ChildrenEntities> getTenants() {
        return tenants;
    }

    public void setTenants(Set<ChildrenEntities> tenants) {
        this.tenants = tenants;
    }

    public Set<RoleEnum> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEnum> roles) {
        this.roles = roles;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<Instruments> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<Instruments> instruments) {
        this.instruments = instruments;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public UserIdentity getUserIdentity() { return userIdentity; }
    public void setUserIdentity(UserIdentity userIdentity) { this.userIdentity = userIdentity; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Users users)) {
            return false;
        }
        return super.equals(users) &&
            Objects.equals(lastName, users.lastName) &&
            Objects.equals(birthDate, users.birthDate) &&
            Objects.equals(email, users.email) &&
            Objects.equals(tenants, users.tenants) &&
            Objects.equals(roles, users.roles) &&
            Objects.equals(active, users.active) &&
            Objects.equals(instruments, users.instruments) &&
            Objects.equals(keycloakId, users.keycloakId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastName, birthDate, email, tenants, roles, active, instruments, keycloakId);
    }

    @Override
    public String toString() {
        return "Users{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", lastName='" + getLastName() + '\'' +
            ", birthDate=" + getBirthDate() +
            ", email='" + getEmail() + '\'' +
            ", tenant='" + getTenants() + '\'' +
            ", roles=" + getRoles() +
            ", active=" + getActive() +
            ", description='" + getDescription() + "'" +
            ", instruments=" + getInstruments() +
            ", keycloakId='" + getKeycloakId() + "'" +
            '}';
    }
}
