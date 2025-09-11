package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonFieldsDTO implements Serializable {

    private Long id;

    @JsonIgnore
    private String userId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommonFieldsDTO commonFieldsDTO)) {
            return false;
        }

        if (this.id == null) {
            return false;
        }

        return Objects.equals(this.id, commonFieldsDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }
}
