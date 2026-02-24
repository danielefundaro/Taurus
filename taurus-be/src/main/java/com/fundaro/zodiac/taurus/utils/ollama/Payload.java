package com.fundaro.zodiac.taurus.utils.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Payload implements Serializable {
    @JsonProperty("model")
    private final String model;

    @JsonProperty("prompt")
    private final String prompt;

    @JsonProperty("stream")
    private final Boolean stream;

    @JsonProperty("images")
    private List<String> images;

    public Payload(String model, String prompt, Boolean stream, List<String> images) {
        this.model = model;
        this.prompt = prompt;
        this.stream = stream;
        this.images = images;
    }

    public Payload(String model, String prompt) {
        this(model, prompt, false, new ArrayList<>());
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public Boolean getStream() {
        return stream;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
