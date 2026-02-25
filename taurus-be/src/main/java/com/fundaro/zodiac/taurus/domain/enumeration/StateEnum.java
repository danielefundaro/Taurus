package com.fundaro.zodiac.taurus.domain.enumeration;

public enum StateEnum {
    DRAFT,
    COMPLETE,
    PUBLIC,
    TRASHED;

    @Override
    public String toString() {
        return mapToString(this);
    }

    public static String mapToString(StateEnum stateEnum) {
        String state = "";

        switch (stateEnum) {
            case DRAFT -> state = "DRAFT";
            case COMPLETE -> state = "COMPLETE";
            case PUBLIC -> state = "PUBLIC";
            case TRASHED -> state = "TRASHED";
        }

        return state;
    }
}
