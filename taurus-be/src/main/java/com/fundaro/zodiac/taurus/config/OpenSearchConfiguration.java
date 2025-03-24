package com.fundaro.zodiac.taurus.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import jakarta.xml.bind.DatatypeConverter;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.mapping.*;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class OpenSearchConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchConfiguration.class);

    private final Environment env;

    private final OpenSearchService openSearchService;

    private final String CHANGELOGINDEXNAME = "changelog";

    public OpenSearchConfiguration(Environment env, OpenSearchService openSearchService) {
        this.env = env;
        this.openSearchService = openSearchService;
    }

    @Bean
    public Boolean opensearch() throws IOException, NoSuchAlgorithmException {
        LOG.debug("Configuring OpenSearch");
        createChangeLogIndex();
        Set<Map<String, String>> master = getChangeLog();

        if (master != null && !master.isEmpty()) {
            for (Map<String, String> change : master) {
                if (change.containsKey("file")) {
                    String filename = change.get("file");
                    TypeReference<Map<String, Object>> typeReference = new TypeReference<>() {
                    };
                    Map<String, Object> mapIndex = getMaps(filename, typeReference);

                    // If the file exists, check and add index
                    if (mapIndex != null) {
                        String id = mapIndex.containsKey("id") ? mapIndex.get("id").toString() : null;
                        String indexName = mapIndex.containsKey("index_name") ? mapIndex.get("index_name").toString() : null;
                        Object properties = mapIndex.getOrDefault("properties", null);
                        Map<String, String> loadData = null;

                        // Remove data before the md5 check sum
                        if (mapIndex.containsKey("load_data")) {
                            loadData = (Map<String, String>) mapIndex.remove("load_data");
                        }

                        if (id != null && indexName != null && properties != null) {
                            // Check if the index was created
                            SearchResponse<Map> result = openSearchService.search(builder -> builder
                                .index(CHANGELOGINDEXNAME)
                                .from(0)
                                .size(1)
                                .query(q -> q.bool(b -> b.must(m -> m.term(t -> t.field("id").value(v -> v.stringValue(id)))))), Map.class);

                            // Obtain md5
                            MessageDigest md = MessageDigest.getInstance("MD5");
                            md.update(mapIndex.toString().getBytes(StandardCharsets.UTF_8));
                            byte[] digest = md.digest();
                            String md5checkSum = DatatypeConverter.printHexBinary(digest).toUpperCase();

                            if (result.hits().total().value() != 0) {
                                Map<?, ?> source = result.hits().hits().get(0).source();

                                if (source != null && source.containsKey("md5sum")) {
                                    String sourceMd5 = source.get("md5sum").toString();

                                    if (!md5checkSum.equals(sourceMd5)) {
                                        LOG.error("Something changed in the file {}, before md5checksum was {}, now is {}. Checksum does not match", filename, sourceMd5, md5checkSum);
                                    }
                                }
                            } else {
                                // Create the index for the first time
                                CreateIndexResponse response = openSearchService.createIndex(indexName, new TypeMapping.Builder().properties(getProperties((Map<String, Map<String, Object>>) properties)));

                                if (Boolean.TRUE.equals(response.acknowledged())) {
                                    LOG.debug("Index {} created", indexName);

                                    Map<String, Object> indexChangeLog = new HashMap<>(Map.ofEntries(
                                        Map.entry("id", id),
                                        Map.entry("filename", filename),
                                        Map.entry("index_name", indexName),
                                        Map.entry("data_executed", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())),
                                        Map.entry("md5sum", md5checkSum),
                                        Map.entry("description", String.format("Create index %s", indexName))
                                    ));

                                    if (mapIndex.containsKey("author")) {
                                        indexChangeLog.putIfAbsent("author", mapIndex.get("author"));
                                    }

                                    if (mapIndex.containsKey("context")) {
                                        indexChangeLog.putIfAbsent("context", mapIndex.get("context"));
                                    }

                                    IndexResponse indexResponse = openSearchService.index(new IndexRequest.Builder<Map<String, Object>>().index(CHANGELOGINDEXNAME).document(indexChangeLog).build());

                                    if (indexResponse.result() == Result.Created || indexResponse.result() == Result.Updated) {
                                        LOG.debug("Reference created for the index {} into {}", indexName, CHANGELOGINDEXNAME);
                                    } else {
                                        LOG.error("Reference failed for the index {} into {}", indexName, CHANGELOGINDEXNAME);
                                    }
                                } else {
                                    LOG.error("Something went wrong when trying to create the index {}", indexName);
                                }
                            }

                            // Check and update data
                            if (loadData != null) {
                                String dataId = loadData.getOrDefault("id", null);
                                String dataFilename = loadData.getOrDefault("file", null);
                                String author = loadData.getOrDefault("author", null);
                                String context = loadData.getOrDefault("context", null);
                                TypeReference<Set<Map<String, Object>>> dataTypeReference = new TypeReference<>() {
                                };
                                Set<Map<String, Object>> dataMapIndex = getMaps(dataFilename, dataTypeReference);

                                if (dataMapIndex != null) {
                                    if (dataId != null && dataFilename != null) {
                                        SearchResponse<Map> dataResult = openSearchService.search(builder -> builder
                                            .index(CHANGELOGINDEXNAME)
                                            .from(0)
                                            .size(1)
                                            .query(q -> q.bool(b -> b.must(m -> m.term(t -> t.field("id").value(v -> v.stringValue(dataId)))))), Map.class);

                                        // Obtain md5
                                        MessageDigest dataMd = MessageDigest.getInstance("MD5");
                                        dataMd.update(dataMapIndex.toString().getBytes(StandardCharsets.UTF_8));
                                        byte[] dataDigest = dataMd.digest();
                                        String dataMd5checkSum = DatatypeConverter.printHexBinary(dataDigest).toUpperCase();

                                        if (dataResult.hits().total().value() != 0) {
                                            Map<?, ?> source = dataResult.hits().hits().get(0).source();

                                            if (source != null && source.containsKey("md5sum")) {
                                                String sourceMd5 = source.get("md5sum").toString();

                                                if (!dataMd5checkSum.equals(sourceMd5)) {
                                                    LOG.error("Something changed in the file {}, before md5checksum was {}, now is {}. Checksum does not match", dataFilename, sourceMd5, dataMd5checkSum);
                                                }
                                            }
                                        } else {
                                            for (Map<String, Object> data : dataMapIndex) {
                                                openSearchService.index(new IndexRequest.Builder<Map<String, Object>>().index(indexName).document(data).build());
                                            }

                                            Map<String, Object> dataChangeLog = new HashMap<>(Map.ofEntries(
                                                Map.entry("id", dataId),
                                                Map.entry("filename", dataFilename),
                                                Map.entry("data_executed", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date())),
                                                Map.entry("md5sum", dataMd5checkSum),
                                                Map.entry("description", String.format("Fill index %s with default values", indexName))
                                            ));

                                            if (Strings.isNotBlank(author)) {
                                                dataChangeLog.put("author", author);
                                            }

                                            if (Strings.isNotBlank(context)) {
                                                dataChangeLog.put("context", context);
                                            }

                                            IndexResponse indexResponse = openSearchService.index(new IndexRequest.Builder<Map<String, Object>>().index(CHANGELOGINDEXNAME).document(dataChangeLog).build());

                                            if (indexResponse.result() == Result.Created || indexResponse.result() == Result.Updated) {
                                                LOG.debug("Data reference created for the index {} into {}", indexName, CHANGELOGINDEXNAME);
                                            } else {
                                                LOG.error("Data reference failed for the index {} into {}", indexName, CHANGELOGINDEXNAME);
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LOG.error("Missing information into the file {}", filename);
                        }
                    } else {
                        LOG.warn("Skip file {} because does not exists", filename);
                    }
                }
            }
        }
        return null;
    }

    private void createChangeLogIndex() throws IOException {
        TypeMapping.Builder mappingBuilder = new TypeMapping.Builder().properties(Map.ofEntries(
            Map.entry("id", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("author", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("filename", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("index_name", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("data_executed", new Property.Builder().date(new DateProperty.Builder().build()).build()),
            Map.entry("md5sum", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("description", new Property.Builder().text(new TextProperty.Builder().build()).build()),
            Map.entry("context", new Property.Builder().text(new TextProperty.Builder().build()).build())
        ));

        openSearchService.createIndex(CHANGELOGINDEXNAME, mappingBuilder);
    }

    private Set<Map<String, String>> getChangeLog() throws IOException {
        TypeReference<Set<Map<String, String>>> typeReference = new TypeReference<>() {
        };
        return getMaps("/config/opensearch/master.json", typeReference);
    }

    private <T> T getMaps(String resourceName, TypeReference<T> typeReference) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                String json = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
                return mapper.readValue(json, typeReference);
            }
        }

        return null;
    }

    private Map<String, Property> getProperties(Map<String, Map<String, Object>> properties) {
        Map<String, Property> map = new HashMap<>();
        properties.forEach((key, value) -> {
            if (value.containsKey("type")) {
                Property p = switch (value.get("type").toString()) {
                    case "alias" -> new Property.Builder().alias(new FieldAliasProperty.Builder().build()).build();
                    case "boolean" -> new Property.Builder().boolean_(new BooleanProperty.Builder().build()).build();
                    case "binary" -> new Property.Builder().binary(new BinaryProperty.Builder().build()).build();
                    case "completion" ->
                        new Property.Builder().completion(new CompletionProperty.Builder().build()).build();
                    case "date" -> new Property.Builder().date(new DateProperty.Builder().build()).build();
                    case "dateRange" ->
                        new Property.Builder().dateRange(new DateRangeProperty.Builder().build()).build();
                    case "double" -> new Property.Builder().double_(new DoubleNumberProperty.Builder().build()).build();
                    case "doubleRange" ->
                        new Property.Builder().doubleRange(new DoubleRangeProperty.Builder().build()).build();
                    case "float" -> new Property.Builder().float_(new FloatNumberProperty.Builder().build()).build();
                    case "geo_point" -> new Property.Builder().geoPoint(new GeoPointProperty.Builder().build()).build();
                    case "geo_shape" -> new Property.Builder().geoShape(new GeoShapeProperty.Builder().build()).build();
                    case "half_float" ->
                        new Property.Builder().halfFloat(new HalfFloatNumberProperty.Builder().build()).build();
                    case "integer" ->
                        new Property.Builder().integer(new IntegerNumberProperty.Builder().build()).build();
                    case "ip" -> new Property.Builder().ip(new IpProperty.Builder().build()).build();
                    case "ip_range" -> new Property.Builder().ipRange(new IpRangeProperty.Builder().build()).build();
                    case "keyword" -> new Property.Builder().keyword(new KeywordProperty.Builder().build()).build();
                    case "long" -> new Property.Builder().long_(new LongNumberProperty.Builder().build()).build();
                    case "long_range" ->
                        new Property.Builder().longRange(new LongRangeProperty.Builder().build()).build();
                    case "object" -> {
                        if (value.containsKey("properties")) {
                            Map<String, Map<String, Object>> subProperties = (Map<String, Map<String, Object>>) value.get("properties");
                            yield new Property.Builder().object(new ObjectProperty.Builder().properties(getProperties(subProperties)).build()).build();
                        }
                        yield null;
                    }
                    case "percolator" ->
                        new Property.Builder().percolator(new PercolatorProperty.Builder().build()).build();
                    case "rank_feature" ->
                        new Property.Builder().rankFeature(new RankFeatureProperty.Builder().build()).build();
                    case "rank_features" ->
                        new Property.Builder().rankFeatures(new RankFeaturesProperty.Builder().build()).build();
                    case "search_as_you_type" ->
                        new Property.Builder().searchAsYouType(new SearchAsYouTypeProperty.Builder().build()).build();
                    case "text" -> new Property.Builder().text(new TextProperty.Builder().build()).build();
                    case "token_count" ->
                        new Property.Builder().tokenCount(new TokenCountProperty.Builder().build()).build();
                    default -> null;
                };

                if (p != null) {
                    map.put(key, p);
                }
            }
        });

        return map;
    }
}
