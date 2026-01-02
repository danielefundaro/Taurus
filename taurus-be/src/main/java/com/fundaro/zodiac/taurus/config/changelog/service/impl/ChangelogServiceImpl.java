package com.fundaro.zodiac.taurus.config.changelog.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogFile;
import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogRecord;
import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import org.apache.logging.log4j.util.Strings;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.mapping.*;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Service
public class ChangelogServiceImpl implements ChangelogService {

    private static final Logger LOG = LoggerFactory.getLogger(ChangelogService.class);

    private final String CHANGELOGINDEXNAME = "changelog";

    private final OpenSearchService openSearchService;

    public ChangelogServiceImpl(OpenSearchService openSearchService) {
        this.openSearchService = openSearchService;
    }

    @Override
    public void createChangeLogIndex() throws IOException {
        openSearchService.createIndex(CHANGELOGINDEXNAME, ChangelogRecord.builder());
    }

    @Override
    public void createIndex(ChangelogFile changelogFile, String filename) throws IOException, NoSuchAlgorithmException {
        if (changelogFile.isValid() && changelogFile.isPropertiesSpecified()) {
            String md5checkSum = checksum(changelogFile, changelogFile);

            if (Strings.isNotBlank(md5checkSum)) {
                // Create the index for the first time
                CreateIndexResponse response = openSearchService.createIndex(changelogFile.getIndexName(), new TypeMapping.Builder().properties(getProperties(changelogFile.getProperties())));

                if (Boolean.TRUE.equals(response.acknowledged())) {
                    LOG.debug("Index {} created", changelogFile.getIndexName());
                    addRecordToDatabase(changelogFile, filename, md5checkSum, String.format("Create index %s", changelogFile.getIndexName()), null);
                } else {
                    LOG.error("Something went wrong when trying to create the index {}", changelogFile.getIndexName());
                }
            }
        } else {
            missingInformationErrorLog(filename);
        }
    }

    @Override
    public void loadData(ChangelogFile changelogFile, String filename) throws IOException, NoSuchAlgorithmException {
        if (changelogFile.isValid() && changelogFile.isFileSpecified()) {
            TypeReference<Set<Map<String, Object>>> dataTypeReference = new TypeReference<>() {
            };
            Set<Map<String, Object>> dataMapIndex = getMaps(changelogFile.getFile(), dataTypeReference);

            if (dataMapIndex != null) {
                String md5checkSum = checksum(changelogFile, changelogFile);

                if (Strings.isNotBlank(md5checkSum)) {
                    for (Map<String, Object> data : dataMapIndex) {
                        openSearchService.index(new IndexRequest.Builder<Map<String, Object>>().index(changelogFile.getIndexName()).document(data).build());
                    }

                    addRecordToDatabase(changelogFile, changelogFile.getFile(), md5checkSum, String.format("Fill index %s with default values", changelogFile.getIndexName()), "Data");
                }
            }
        } else {
            missingInformationErrorLog(filename);
        }
    }

    @Override
    public <T> T getMaps(String resourceName, TypeReference<T> typeReference) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                String json = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
                return mapper.readValue(json, typeReference);
            }
        }

        return null;
    }

    private static void missingInformationErrorLog(String filename) {
        LOG.error("Missing information into the file {}", filename);
    }

    private void addRecordToDatabase(ChangelogFile changelogFile, String filename, String md5checkSum, String description, String type) throws IOException {
        ChangelogRecord changelogRecord = new ChangelogRecord(changelogFile, filename, md5checkSum, description);
        IndexResponse indexResponse = openSearchService.index(new IndexRequest.Builder<ChangelogRecord>().index(CHANGELOGINDEXNAME).document(changelogRecord).build());
        type = Strings.isNotBlank(type) ? type + " reference" : "Reference";

        if (indexResponse.result() == Result.Created || indexResponse.result() == Result.Updated) {
            LOG.debug("{} created for the index {} into {}", type, changelogFile.getIndexName(), CHANGELOGINDEXNAME);
        } else {
            LOG.error("{} failed for the index {} into {}", type, changelogFile.getIndexName(), CHANGELOGINDEXNAME);
        }
    }

    private <T> String checksum(ChangelogFile loadData, T dataMapIndex) throws IOException, NoSuchAlgorithmException {
        // Check if the index was created
        SearchResponse<ChangelogRecord> dataResult = openSearchService.search(builder -> builder
            .index(CHANGELOGINDEXNAME)
            .from(0)
            .size(1)
            .query(q -> q.bool(b -> b.must(m -> m.term(t -> t.field("id").value(v -> v.stringValue(loadData.getId())))))), ChangelogRecord.class);

        // Obtain md5
        String md5checkSum = ChangelogRecord.calcMd5sum(dataMapIndex);
        ChangelogRecord source = null;

        if (dataResult.hits().total().value() > 0) {
            source = dataResult.hits().hits().get(0).source();
        }

        if (source != null && source.getMd5sum() != null) {
            if (!md5checkSum.equals(source.getMd5sum())) {
                LOG.error("Something changed in the file {}, before md5checksum was {}, now is {}. Checksum does not match", loadData.getFile(), source.getMd5sum(), md5checkSum);
            }

            return null;
        }

        return md5checkSum;
    }

    private Map<String, Property> getProperties(Map<String, Map<String, Object>> properties) {
        Map<String, Property> map = new HashMap<>();
        properties.forEach((key, value) -> {
            String format = null;
            Map<String, Property> fields = null;

            if (value.containsKey("format")) {
                format = value.get("format").toString();
            }

            if (value.containsKey("fields")) {
                fields = getProperties((Map<String, Map<String, Object>>) value.get("fields"));
            }

            if (value.containsKey("type")) {
                Property p = switch (value.get("type").toString()) {
                    case "alias" -> new Property.Builder().alias(new FieldAliasProperty.Builder().build()).build();
                    case "boolean" -> new Property.Builder().boolean_(new BooleanProperty.Builder().build()).build();
                    case "binary" -> new Property.Builder().binary(new BinaryProperty.Builder().build()).build();
                    case "completion" ->
                        new Property.Builder().completion(new CompletionProperty.Builder().build()).build();
                    case "date" -> {
                        DateProperty.Builder dateProperty = new DateProperty.Builder();

                        if (Strings.isNotBlank(format)) {
                            dateProperty.format(format);
                        }

                        yield new Property.Builder().date(dateProperty.build()).build();
                    }
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
                    case "text" -> {
                        TextProperty.Builder textPropertyBuilder = new TextProperty.Builder();

                        if (fields != null) {
                            textPropertyBuilder.fields(fields);
                        }

                        yield new Property.Builder().text(textPropertyBuilder.build()).build();
                    }
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
