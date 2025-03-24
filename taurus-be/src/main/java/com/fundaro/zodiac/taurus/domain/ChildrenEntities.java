package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChildrenEntities {

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
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ChildrenEntities)) {
            return false;
        }

        return getIndex() != null && getIndex().equals(((ChildrenEntities) o).getIndex());
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
