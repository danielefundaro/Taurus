package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChildrenEntitiesDTO implements Serializable {

    private String index;

    private String name;

    private Long order;

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getOrder() {
        return order;
    }

    public void setOrder(Long order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ChildrenEntitiesDTO)) {
            return false;
        }

        return getIndex() != null && getIndex().equals(((ChildrenEntitiesDTO) o).getIndex());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getIndex(), this.getName(), this.getOrder());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "{" +
            (getIndex() != null ? "index=" + getIndex() : "") +
            (getName() != null ? "name=" + getName() : "") +
            (getOrder() != null ? "order=" + getOrder() : "") +
            "}";
    }
}
