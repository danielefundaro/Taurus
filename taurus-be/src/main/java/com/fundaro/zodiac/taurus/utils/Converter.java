package com.fundaro.zodiac.taurus.utils;

import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.RangeQuery;
import org.springframework.lang.NonNull;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Converter {

    public static String camelCaseToKebabCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static String camelCaseToSnakeCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static List<String> pdfToImage(byte[] content, String filename, String destinationPath) throws IOException {
        String formatName = "png";
        int dpi = 300;
        List<String> files = new ArrayList<>();
        filename = filename.replace(" ", "_");

        // Create destination folder
        File destinationFile = new File(Paths.get(destinationPath, filename).toString());

        if (!destinationFile.exists()) {
            destinationFile.mkdirs();
        }

        // Read the pdf file and create images for each page
        try (PDDocument pdDocument = Loader.loadPDF(content)) {
            PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);

            for (int page = 0; page < pdDocument.getNumberOfPages(); ++page) {
                String destinationFilePath = String.format("%s/%s.%s", destinationFile.getPath(), page + 1, formatName);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                ImageIOUtil.writeImage(bim, destinationFilePath, dpi);
                files.add(destinationFilePath);

                // Remove external bounding box
//                Rectangle bounds = getBounds(bim, Color.WHITE);
//                BufferedImage trimmed = bim.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
//                ImageIOUtil.writeImage(trimmed, destinationFilePath, dpi);
            }
        }

        return files;
    }

    public static byte[] objectToBytes(Object obj) throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try (ObjectOutputStream ois = new ObjectOutputStream(boas)) {
            ois.writeObject(obj);
            return boas.toByteArray();
        }
    }

    public static Object bytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(is)) {
            return ois.readObject();
        }
    }

    public static List<Query> stringFilterToQuery(String fieldName, StringFilter fieldValue) {
        List<Query> queries = new ArrayList<>();
        List<Query> notQueries = new ArrayList<>();

        if (fieldValue != null && fieldName != null) {
            String finalFieldName = camelCaseToSnakeCase(fieldName);

            if (Strings.isNotBlank(fieldValue.getEquals())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.stringValue(fieldValue.getEquals())))));
            }

            if (Strings.isNotBlank(fieldValue.getNotEquals())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.stringValue(fieldValue.getNotEquals())))));
            }

            if (Strings.isNotBlank(fieldValue.getContains())) {
                queries.add(Query.of(f -> f.queryString(m -> m.query(String.format("*%s*", fieldValue.getContains())).fields(List.of(finalFieldName)))));
            }

            if (Strings.isNotBlank(fieldValue.getDoesNotContain())) {
                notQueries.add(Query.of(f -> f.queryString(m -> m.query(String.format("*%s*", fieldValue.getDoesNotContain())).fields(List.of(finalFieldName)))));
            }

            if (Objects.nonNull(fieldValue.getIn()) && !fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().anyMatch(Strings::isNotBlank)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().stringValue(v).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (Objects.nonNull(fieldValue.getNotIn()) && !fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().anyMatch(Strings::isNotBlank)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().stringValue(v).build()).toList();
                notQueries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!notQueries.isEmpty()) {
                queries.add(Query.of(f -> f.bool(b -> b.mustNot(notQueries))));
            }
        }

        return queries;
    }

    public static List<Query> dateFilterToQuery(String fieldName, DateFilter fieldValue) {
        List<Query> queries = new ArrayList<>();
        List<Query> notQueries = new ArrayList<>();

        if (fieldValue != null && fieldName != null) {
            String finalFieldName = camelCaseToSnakeCase(fieldName);

            if (!Objects.isNull(fieldValue.getEquals())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getEquals().getTime())))));
            }

            if (!Objects.isNull(fieldValue.getNotEquals())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getNotEquals().getTime())))));
            }

            if (!fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().noneMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().longValue(v.getTime()).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().noneMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().longValue(v.getTime()).build()).toList();
                notQueries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!notQueries.isEmpty()) {
                queries.add(Query.of(f -> f.bool(b -> b.mustNot(notQueries))));
            }

            if (!Objects.isNull(fieldValue.getLessThanOrEqual()) ||
                !Objects.isNull(fieldValue.getLessThanOrEqual()) ||
                !Objects.isNull(fieldValue.getLessThan()) ||
                !Objects.isNull(fieldValue.getGreaterThan())) {
                RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder().field(finalFieldName);

                if (!Objects.isNull(fieldValue.getLessThanOrEqual())) {
                    rangeQueryBuilder.lte(JsonData.of(fieldValue.getLessThanOrEqual()));
                }

                if (!Objects.isNull(fieldValue.getGreaterThanOrEqual())) {
                    rangeQueryBuilder.gte(JsonData.of(fieldValue.getGreaterThanOrEqual()));
                }

                if (!Objects.isNull(fieldValue.getLessThan())) {
                    rangeQueryBuilder.lt(JsonData.of(fieldValue.getLessThan()));
                }

                if (!Objects.isNull(fieldValue.getGreaterThan())) {
                    rangeQueryBuilder.gt(JsonData.of(fieldValue.getGreaterThan()));
                }

                queries.add(Query.of(f -> f.range(rangeQueryBuilder.build())));
            }
        }

        return queries;
    }

    public static <T> List<Query> generalFilterToQuery(String fieldName, @NonNull Filter<T> fieldValue) {
        return stringFilterToQuery(fieldName, (StringFilter) fieldValue);
    }

    private static Rectangle getBounds(BufferedImage img, Color fillColor) {
        int width = img.getWidth(), height = img.getHeight();
        int top = height / 2, left = width / 2;
        int bottom = top, right = left;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (new Color(img.getRGB(x, y), true).equals(fillColor)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);

                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }
}
