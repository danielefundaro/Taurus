package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

public class PushSubscriptionDTO extends CommonFieldsDTO {

    @NotNull
    private String endpoint;

    @NotNull
    private String p256dh;

    @NotNull
    private String auth;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PushSubscriptionDTO)) return false;
        return super.equals(o);
    }

    @Override
    public String toString() {
        return "PushSubscriptionDTO{id=" + getId() + ", userId='" + getUserId() + "', endpoint='" + endpoint + "'}";
    }
}
