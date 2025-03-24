package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.Instruments;

/**
 * A DTO for the {@link Instruments} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstrumentsDTO extends CommonFieldsOpenSearchDTO {

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InstrumentsDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "InstrumentsDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
