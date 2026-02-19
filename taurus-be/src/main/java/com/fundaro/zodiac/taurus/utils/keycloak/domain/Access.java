package com.fundaro.zodiac.taurus.utils.keycloak.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Access implements Serializable {
    private Boolean view;
    private Boolean configure;
    private Boolean manage;

    public Boolean getView() {
        return view;
    }

    public void setView(Boolean view) {
        this.view = view;
    }

    public Boolean getConfigure() {
        return configure;
    }

    public void setConfigure(Boolean configure) {
        this.configure = configure;
    }

    public Boolean getManage() {
        return manage;
    }

    public void setManage(Boolean manage) {
        this.manage = manage;
    }

    @Override
    public String toString() {
        return "Access{" +
            "view=" + view +
            ", configure=" + configure +
            ", manage=" + manage +
            '}';
    }
}
