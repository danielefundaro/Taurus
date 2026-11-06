package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentState;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

/**
 * A version of a legal document as the super administrator manages it. State, publication, activation, fingerprint
 * and URL are written by the server only: the activation has its own endpoints.
 */
public class LegalDocumentDTO {

    private Long id;

    @NotNull
    private LegalDocumentType documentType;

    @NotBlank
    private String version;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotNull
    private LegalDocumentAction action;

    private Boolean required;

    /** Markdown text; left out of the list. */
    private String content;

    @Size(max = 1000)
    private String changeSummary;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LegalDocumentState state;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private ZonedDateTime publishedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Boolean active;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String contentSha256;

    /** Address of a document published before the text lived in Taurus. */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String url;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LegalDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(LegalDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LegalDocumentAction getAction() {
        return action;
    }

    public void setAction(LegalDocumentAction action) {
        this.action = action;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public LegalDocumentState getState() {
        return state;
    }

    public void setState(LegalDocumentState state) {
        this.state = state;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(ZonedDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
