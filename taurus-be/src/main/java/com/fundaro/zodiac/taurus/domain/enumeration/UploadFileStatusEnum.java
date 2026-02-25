package com.fundaro.zodiac.taurus.domain.enumeration;

public enum UploadFileStatusEnum {
    TO_PROCESS,
    IN_PROGRESS,
    DONE,
    ERROR,
    NOT_FOUND;

    @Override
    public String toString() {
        return mapToString(this);
    }

    public static String mapToString(UploadFileStatusEnum uploadFileStatusEnum) {
        String status = "";

        switch (uploadFileStatusEnum) {
            case TO_PROCESS -> status = "TO_PROCESS";
            case IN_PROGRESS -> status = "IN_PROGRESS";
            case DONE -> status = "DONE";
            case ERROR -> status = "ERROR";
            case NOT_FOUND -> status = "NOT_FOUND";
        }

        return status;
    }
}
