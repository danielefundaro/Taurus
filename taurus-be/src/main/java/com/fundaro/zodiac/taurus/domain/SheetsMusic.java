package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SheetsMusic extends ChildrenEntities {

    private Set<ChildrenEntities> instruments;

    public Set<ChildrenEntities> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntities> instruments) {
        this.instruments = instruments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SheetsMusic)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "{" +
            (getIndex() != null ? "index=" + getIndex() : "") +
            (getName() != null ? "name=" + getName() : "") +
            (getOrder() != null ? "order=" + getOrder() : "") +
            (getInstruments() != null ? "instruments=" + getInstruments() : "") +
            "}";
    }
}
