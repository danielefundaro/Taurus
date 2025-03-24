package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SheetsMusicDTO extends ChildrenEntitiesDTO {

    private Set<ChildrenEntitiesDTO> instruments;

    public Set<ChildrenEntitiesDTO> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntitiesDTO> instruments) {
        this.instruments = instruments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SheetsMusicDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getInstruments(), super.hashCode());
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
