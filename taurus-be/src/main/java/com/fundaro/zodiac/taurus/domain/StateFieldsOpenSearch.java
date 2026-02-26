package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StateFieldsOpenSearch extends CommonFieldsOpenSearch {

    @Enumerated(EnumType.STRING)
    private StateEnum state;

    public StateEnum getState() {
        return state;
    }

    public void setState(StateEnum state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof StateFieldsOpenSearch)) {
            return false;
        }

        return super.equals(o);
    }
}
