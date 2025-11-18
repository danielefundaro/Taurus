package com.fundaro.zodiac.taurus.config.changelog.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ActionEnum {
    CREATE_INDEX("create_index"),
    UPDATE_INDEX("update_index"),
    DELETE_INDEX("delete_index"),
    LOAD_DATA("load_data"),
    UPDATE_DATA("update_data"),
    DELETE_DATA("delete_data");

    private final String value;

    ActionEnum(String value) {
        this.value = value;
    }

    @JsonValue
    @Override
    public String toString() {
        return value;
    }
}
