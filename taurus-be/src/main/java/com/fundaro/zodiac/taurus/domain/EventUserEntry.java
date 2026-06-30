package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventUserEntry extends ChildrenEntities {

    @JsonProperty("response_date")
    private Date responseDate;

    @JsonProperty("last_name")
    private String lastName;

    public Date getResponseDate() {
        return responseDate;
    }

    public void setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventUserEntry)) return false;
        return super.equals(o);
    }

    @Override
    public String toString() {
        return "EventUserEntry{" +
            "index='" + getIndex() +
            "', name='" + getName() +
            "', lastName='" + lastName +
            "', responseDate=" + responseDate +
            "}";
    }
}
