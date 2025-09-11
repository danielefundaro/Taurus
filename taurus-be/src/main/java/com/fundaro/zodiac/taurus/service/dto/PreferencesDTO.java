package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Preferences} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PreferencesDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String key;

    private String value;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PreferencesDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PreferencesDTO{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", key='" + getKey() + "'" +
            ", value='" + getValue() + "'" +
            "}";
    }
}
