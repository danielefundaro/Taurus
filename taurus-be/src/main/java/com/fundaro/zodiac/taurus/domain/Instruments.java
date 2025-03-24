package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * A Instruments.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Instruments extends CommonFieldsOpenSearch {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Instruments)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Instruments{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
