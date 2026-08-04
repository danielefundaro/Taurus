package com.fundaro.zodiac.taurus.utils;

import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import com.fundaro.zodiac.taurus.utils.pdf.PdfAnnotations;
import com.fundaro.zodiac.taurus.utils.pdf.PdfCropRegion;
import org.apache.commons.io.FileUtils;
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
import tech.jhipster.service.filter.BooleanFilter;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;
import tech.jhipster.service.filter.StringFilter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

public class Converter {

    public static String camelCaseToKebabCase(@NonNull String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static String camelCaseToSnakeCase(@NonNull String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String tenantConcatSnakeCase(@NonNull String tenantCode, @NonNull String value) {
        tenantCode = tenantCode.replaceAll(" ", "").toLowerCase();
        return Arrays.stream(new String[]{tenantCode, value}).filter(Strings::isNotBlank).collect(Collectors.joining("_"));
    }

    public static List<String> pdfToImage(byte[] content, String filename, String destinationPath) throws IOException {
        return pdfToImage(content, filename, destinationPath, null);
    }

    /**
     * Converts a PDF to a list of images, one per page.
     * The returned list is always index-aligned with the PDF page count: excluded pages produce a
     * {@code null} entry rather than being dropped, so callers can still use 0-based page indices.
     *
     * @param annotations optional exclusion and crop instructions produced by the frontend manipulator;
     *                    {@code null} means "process all pages without cropping"
     */
    public static List<String> pdfToImage(byte[] content, String filename, String destinationPath, PdfAnnotations annotations) throws IOException {
        String formatName = "png";
        int dpi = 300;
        List<String> files = new ArrayList<>();
        filename = filename.replace(" ", "_");

        Set<Integer> excludedPages = (annotations != null && annotations.getExcludedPages() != null)
            ? new HashSet<>(annotations.getExcludedPages())
            : Collections.emptySet();

        // Group crop regions by page — multiple regions per page are supported
        Map<Integer, List<PdfCropRegion>> cropMap = new HashMap<>();
        if (annotations != null && annotations.getCropRegions() != null) {
            annotations.getCropRegions().forEach(crop ->
                cropMap.computeIfAbsent(crop.getPage(), k -> new ArrayList<>()).add(crop));
        }

        // Create destination folder
        File destinationFile = new File(Paths.get(destinationPath, filename).toString());
        if (!destinationFile.exists()) {
            destinationFile.mkdirs();
        }

        try (PDDocument pdDocument = Loader.loadPDF(content)) {
            PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);

            for (int page = 0; page < pdDocument.getNumberOfPages(); ++page) {
                int pageNum = page + 1; // annotations use 1-based page numbers

                if (excludedPages.contains(pageNum)) {
                    files.add(null); // null preserves index alignment for buildSheets()
                    continue;
                }

                String destinationFilePath = String.format("%s/%s.%s", destinationFile.getPath(), pageNum, formatName);
                BufferedImage bim;
                try {
                    bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                } catch (RuntimeException e) {
                    // PDFBox on Windows may throw RuntimeException on the first render while
                    // the font cache initialises (WindowsFontDirFinder tries to exec cmd.exe).
                    throw new IOException("PDF rendering failed for page " + pageNum + ": " + e.getMessage(), e);
                }

                List<PdfCropRegion> crops = cropMap.get(pageNum);
                if (crops != null && !crops.isEmpty()) {
                    bim = applyCrops(bim, crops);
                }

                ImageIOUtil.writeImage(bim, destinationFilePath, dpi);
                files.add(destinationFilePath);
            }
        }

        return files;
    }

    private static BufferedImage applyCrops(BufferedImage img, List<PdfCropRegion> crops) {
        if (crops.size() == 1) {
            return applyCrop(img, crops.get(0));
        }
        List<BufferedImage> regions = crops.stream()
            .map(c -> applyCrop(img, c))
            .collect(Collectors.toList());
        return combineVertically(regions);
    }

    private static BufferedImage combineVertically(List<BufferedImage> images) {
        int totalHeight = images.stream().mapToInt(BufferedImage::getHeight).sum();
        int maxWidth = images.stream().mapToInt(BufferedImage::getWidth).max().orElse(0);
        BufferedImage combined = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = combined.createGraphics();
        int y = 0;
        for (BufferedImage img : images) {
            g.drawImage(img, 0, y, null);
            y += img.getHeight();
        }
        g.dispose();
        return combined;
    }

    private static BufferedImage applyCrop(BufferedImage img, PdfCropRegion crop) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int x = clamp((int) (crop.getX() * imgW), 0, imgW - 1);
        int y = clamp((int) (crop.getY() * imgH), 0, imgH - 1);
        int w = clamp((int) (crop.getWidth() * imgW), 1, imgW - x);
        int h = clamp((int) (crop.getHeight() * imgH), 1, imgH - y);
        return img.getSubimage(x, y, w, h);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static byte[] objectToBytes(Object obj) throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try (ObjectOutputStream ois = new ObjectOutputStream(boas)) {
            ois.writeObject(obj);
            return boas.toByteArray();
        }
    }

    public static String imageEncodeBase64(String filePath) throws IOException {
        byte[] fileContent = Files.readAllBytes(Paths.get(filePath));
        return Base64.getEncoder().encodeToString(fileContent);
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

    public static List<Query> booleanFilterToQuery(String fieldName, BooleanFilter fieldValue) {
        List<Query> queries = new ArrayList<>();
        List<Query> notQueries = new ArrayList<>();

        if (fieldValue != null && fieldName != null) {
            String finalFieldName = camelCaseToSnakeCase(fieldName);

            if (Objects.nonNull(fieldValue.getEquals())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.booleanValue(fieldValue.getEquals())))));
            }

            if (Objects.nonNull(fieldValue.getNotEquals())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.booleanValue(fieldValue.getNotEquals())))));
            }

            if (Objects.nonNull(fieldValue.getIn()) && !fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().anyMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().booleanValue(v).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (Objects.nonNull(fieldValue.getNotIn()) && !fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().anyMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().booleanValue(v).build()).toList();
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

            if (Objects.nonNull(fieldValue.getEquals())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getEquals().getTime())))));
            }

            if (Objects.nonNull(fieldValue.getNotEquals())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getNotEquals().getTime())))));
            }

            if (Objects.nonNull(fieldValue.getIn()) && !fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().noneMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().longValue(v.getTime()).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (Objects.nonNull(fieldValue.getNotIn()) && !fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().noneMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().longValue(v.getTime()).build()).toList();
                notQueries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!notQueries.isEmpty()) {
                queries.add(Query.of(f -> f.bool(b -> b.mustNot(notQueries))));
            }

            if (!Objects.isNull(fieldValue.getLessThanOrEqual()) ||
                !Objects.isNull(fieldValue.getGreaterThanOrEqual()) ||
                !Objects.isNull(fieldValue.getLessThan()) ||
                !Objects.isNull(fieldValue.getGreaterThan())) {
                RangeQuery.Builder rangeQueryBuilder = new RangeQuery.Builder().field(finalFieldName);

                if (Objects.nonNull(fieldValue.getLessThanOrEqual())) {
                    rangeQueryBuilder.lte(JsonData.of(fieldValue.getLessThanOrEqual()));
                }

                if (Objects.nonNull(fieldValue.getGreaterThanOrEqual())) {
                    rangeQueryBuilder.gte(JsonData.of(fieldValue.getGreaterThanOrEqual()));
                }

                if (Objects.nonNull(fieldValue.getLessThan())) {
                    rangeQueryBuilder.lt(JsonData.of(fieldValue.getLessThan()));
                }

                if (Objects.nonNull(fieldValue.getGreaterThan())) {
                    rangeQueryBuilder.gt(JsonData.of(fieldValue.getGreaterThan()));
                }

                queries.add(Query.of(f -> f.range(rangeQueryBuilder.build())));
            }
        }

        return queries;
    }

    public static List<Query> longFilterToQuery(String fieldName, LongFilter fieldValue) {
        List<Query> queries = new ArrayList<>();
        List<Query> notQueries = new ArrayList<>();

        if (fieldValue != null && fieldName != null) {
            String finalFieldName = camelCaseToSnakeCase(fieldName);

            if (Objects.nonNull(fieldValue.getEquals())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getEquals())))));
            }

            if (Objects.nonNull(fieldValue.getNotEquals())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.longValue(fieldValue.getNotEquals())))));
            }

            if (Objects.nonNull(fieldValue.getIn()) && !fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().anyMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().longValue(v).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (Objects.nonNull(fieldValue.getNotIn()) && !fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().anyMatch(Objects::isNull)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().longValue(v).build()).toList();
                notQueries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!notQueries.isEmpty()) {
                queries.add(Query.of(f -> f.bool(b -> b.mustNot(notQueries))));
            }
        }

        return queries;
    }

    public static <T> List<Query> generalFilterToQuery(String fieldName, Filter<T> fieldValue) {
        List<Query> queries = new ArrayList<>();
        List<Query> notQueries = new ArrayList<>();

        if (fieldValue != null && fieldName != null) {
            String finalFieldName = camelCaseToSnakeCase(fieldName);

            if (Objects.nonNull(fieldValue.getEquals()) && Strings.isNotBlank(fieldValue.getEquals().toString())) {
                queries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.stringValue(fieldValue.getEquals().toString())))));
            }

            if (Objects.nonNull(fieldValue.getNotEquals()) && Strings.isNotBlank(fieldValue.getNotEquals().toString())) {
                notQueries.add(Query.of(f -> f.match(m -> m.field(finalFieldName).query(value -> value.stringValue(fieldValue.getNotEquals().toString())))));
            }

            if (Objects.nonNull(fieldValue.getIn()) && !fieldValue.getIn().isEmpty() && fieldValue.getIn().stream().map(Object::toString).anyMatch(Strings::isNotBlank)) {
                List<FieldValue> values = fieldValue.getIn().stream().map(v -> new FieldValue.Builder().stringValue(v.toString()).build()).toList();
                queries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (Objects.nonNull(fieldValue.getNotIn()) && !fieldValue.getNotIn().isEmpty() && fieldValue.getNotIn().stream().map(Object::toString).anyMatch(Strings::isNotBlank)) {
                List<FieldValue> values = fieldValue.getNotIn().stream().map(v -> new FieldValue.Builder().stringValue(v.toString()).build()).toList();
                notQueries.add(Query.of(f -> f.terms(m -> m.field(finalFieldName).terms(a -> a.value(values)))));
            }

            if (!notQueries.isEmpty()) {
                queries.add(Query.of(f -> f.bool(b -> b.mustNot(notQueries))));
            }
        }

        return queries;
    }

    private static Rectangle getBounds(BufferedImage img, Color fillColor) {
        int width = img.getWidth(), height = img.getHeight();
        int top = height / 2, left = width / 2;
        int bottom = top, right = left;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color pixelColor = new Color(img.getRGB(x, y), true);

                if (pixelColor.getRed() < fillColor.getRed() && pixelColor.getBlue() < fillColor.getBlue() && pixelColor.getGreen() < fillColor.getGreen()) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);

                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }

        // Add 10 pixels of border
        if (left > 10) {
            left -= 10;
        }

        if (top > 10) {
            top -= 10;
        }

        if (right < img.getWidth() - 10) {
            right += 10;
        }

        if (bottom < img.getWidth() - 10) {
            bottom += 10;
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }

    private static Color getAvgColor(BufferedImage img) {
        long width = img.getWidth(), height = img.getHeight(), color = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                Color pixelColor = new Color(img.getRGB(x, y), true);
                color += pixelColor.getRed();
            }
        }

        int avg = (int) (color / (width * height));
        return new Color(avg, avg, avg);
    }
}
