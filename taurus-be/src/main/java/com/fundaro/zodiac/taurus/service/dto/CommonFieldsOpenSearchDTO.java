package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonFieldsOpenSearchDTO implements Serializable {

    private String id;

    private String name;

    private String description;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommonFieldsOpenSearchDTO commonFieldsDTO)) {
            return false;
        }

        if (this.id == null) {
            return false;
        }

        return Objects.equals(this.id, commonFieldsDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id, this.name, this.description);
    }
}
