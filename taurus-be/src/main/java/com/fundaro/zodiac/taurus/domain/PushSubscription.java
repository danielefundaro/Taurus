package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription extends CommonFields {

    @NotNull
    @Column(name = "endpoint", length = 2048)
    private String endpoint;

    @NotNull
    @Column(name = "p256dh", length = 512)
    private String p256dh;

    @NotNull
    @Column(name = "auth", length = 255)
    private String auth;

    public PushSubscription() {
        super();
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PushSubscription)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() { return getClass().hashCode(); }

    @Override
    public String toString() {
        return "PushSubscription{id=" + getId() + ", userId='" + getUserId() + "', endpoint='" + endpoint + "'}";
    }
}
