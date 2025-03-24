package com.fundaro.zodiac.taurus.service.dto;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.LastResearch} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LastResearchDTO extends CommonFieldsDTO {

    private String value;

    private String field;

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
            ", value='" + getValue() + "'" +
            ", field='" + getField() + "'" +
            "}";
    }
}
