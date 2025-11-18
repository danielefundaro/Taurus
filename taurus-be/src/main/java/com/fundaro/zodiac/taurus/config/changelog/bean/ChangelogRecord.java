package com.fundaro.zodiac.taurus.config.changelog.bean;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.xml.bind.DatatypeConverter;
import liquibase.change.Change;
import org.opensearch.client.opensearch._types.mapping.DateProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class ChangelogRecord implements Serializable {

    @JsonProperty("id")
    private String id;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("index_name")
    private String indexName;

    @JsonProperty("action")
    private String action;

    @JsonProperty("data_executed")
    private String dataExecuted;

    @JsonProperty("md5sum")
    private String md5sum;

    @JsonProperty("description")
    private String description;

    @JsonProperty("author")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String author;

    @JsonProperty("context")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String context;

    public ChangelogRecord() {}

    public ChangelogRecord(String id, String indexName, String action, String filename, String context, String author, String dataExecuted, String md5sum, String description) {
        this.id = id;
        this.indexName = indexName;
        this.action = action;
        this.filename = filename;
        this.context = context;
        this.author = author;
        this.dataExecuted = dataExecuted;
        this.md5sum = md5sum;
        this.description = description;
    }

    public ChangelogRecord(String id, String indexName, String action, String filename, String context, String author, String md5sum, String description) {
        this(id, indexName, action, filename, context, author, new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()), md5sum, description);
    }

    public ChangelogRecord(ChangelogFile changelogFile, String filename, String md5sum, String description) {
        this(changelogFile.getId(), changelogFile.getIndexName(), changelogFile.getAction().toString(), filename, changelogFile.getContext(), changelogFile.getAuthor(), md5sum, description);
    }

    public ChangelogRecord(ChangelogFile changelogFile, String md5sum, String description) {
        this(changelogFile, changelogFile.getFile(), md5sum, description);
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getIndexName() {
        return indexName;
    }

    public String getAction() {
        return action;
    }

    public String getDataExecuted() {
        return dataExecuted;
    }

    public String getMd5sum() {
        return md5sum;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public String getContext() {
        return context;
    }

    public static TypeMapping.Builder builder() {
        return new TypeMapping.Builder().properties(Map.ofEntries(
            Map.entry("id", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("author", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("filename", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("index_name", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("action", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("data_executed", new Property.Builder().date(new DateProperty.Builder().build()).build()),
            Map.entry("md5sum", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("description", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("context", new Property.Builder().text(new TextProperty.Builder().build()).build())
        ));
    }

    public static <T> String calcMd5sum(T input) throws NoSuchAlgorithmException {
        MessageDigest dataMd = MessageDigest.getInstance("MD5");
        dataMd.update(input.toString().getBytes(StandardCharsets.UTF_8));
        byte[] dataDigest = dataMd.digest();
        return DatatypeConverter.printHexBinary(dataDigest).toUpperCase();
    }
}
