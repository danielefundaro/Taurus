package com.fundaro.zodiac.taurus.config.changelog.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.config.changelog.enums.ActionEnum;
import com.fundaro.zodiac.taurus.utils.Converter;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.apache.logging.log4j.util.Strings;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class ChangelogFile implements Serializable {

    @JsonProperty("id")
    private String id;

    @JsonProperty("index_name")
    private String indexName;

    @JsonProperty("action")
    @Enumerated(EnumType.STRING)
    private ActionEnum action;

    @JsonProperty("author")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String author;

    @JsonProperty("context")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String context;

    @JsonProperty("properties")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Map<String, Object>> properties;

    @JsonProperty("file")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String file;

    public String getId() {
        return id;
    }

    public String getIndexName() {
        return indexName;
    }

    public ActionEnum getAction() {
        return action;
    }

    public String getAuthor() {
        return author;
    }

    public String getContext() {
        return context;
    }

    public Map<String, Map<String, Object>> getProperties() {
        return properties;
    }

    public String getFile() {
        return file;
    }

    public void setTenantCode(String tenantCode) {
        this.indexName = Converter.tenantConcatSnakeCase(tenantCode, indexName);
        this.id = Converter.tenantConcatSnakeCase(tenantCode, id);
    }

    public boolean isValid() {
        return Strings.isNotBlank(id)
            && Strings.isNotBlank(indexName)
            && action != null
            && Strings.isNotBlank(action.toString());
    }

    public boolean isAuthorSpecified() {
        return Strings.isNotBlank(author);
    }

    public boolean isContextSpecified() {
        return Strings.isNotBlank(context);
    }

    public boolean isPropertiesSpecified() {
        return properties != null && !properties.isEmpty();
    }

    public boolean isFileSpecified() {
        return Strings.isNotBlank(file);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChangelogFile that)) {
            return false;
        }
        return Objects.equals(id, that.id)
            && Objects.equals(indexName, that.indexName)
            && Objects.equals(action, that.action)
            && Objects.equals(author, that.author)
            && Objects.equals(context, that.context)
            && Objects.equals(properties, that.properties)
            && Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, indexName, action, author, context, properties, file);
    }

    @Override
    public String toString() {
        return "ChangelogFile{" +
            "id='" + id + "'" +
            ", indexName='" + indexName + "'" +
            ", action='" + action + "'" +
            (isAuthorSpecified() ? ", author='" + author + "'" : "") +
            (isContextSpecified() ? ", context='" + context + "'" : "") +
            (isPropertiesSpecified() ? ", properties=" + properties : "") +
            (isFileSpecified() ? ", file='" + file + "'" : "") +
            "}";
    }
}
