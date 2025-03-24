package com.fundaro.zodiac.taurus.rabbitmq;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.io.Serializable;

public class UploadFilesPackage implements Serializable {
    @JsonProperty("queue_id")
    private String queueId;

    @JsonProperty("user_token")
    private AbstractAuthenticationToken abstractAuthenticationToken;

    public UploadFilesPackage(String queueId, AbstractAuthenticationToken abstractAuthenticationToken) {
        this.queueId = queueId;
        this.abstractAuthenticationToken = abstractAuthenticationToken;
    }

    public String getQueueId() {
        return queueId;
    }

    public AbstractAuthenticationToken getAbstractAuthenticationToken() {
        return abstractAuthenticationToken;
    }
}
