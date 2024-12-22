package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.LastResearch} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LastResearchDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String userId;

    private String value;

    private String field;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LastResearchDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LastResearchDTO{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", value='" + getValue() + "'" +
            ", field='" + getField() + "'" +
            "}";
    }
}
