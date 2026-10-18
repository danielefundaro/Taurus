package com.fundaro.zodiac.taurus.service.scoreplayback.omr;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Base64;
import java.util.List;

/** Data exchanged with the OMR service. */
public final class OmrDtos {

    public static final String DONE = "DONE";

    private OmrDtos() {}

    /** A page image sent for recognition, in reading order. */
    public record PageImage(String fileName, String mimeType, byte[] bytes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecognitionResponse(List<EngineResult> results) {}

    /**
     * What one engine read.
     *
     * @param musicXml compressed MusicXML (.mxl) in Base64, present when the engine succeeded
     * @param layout   position of each measure on the pages; {@code null} when the engine does not report it
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EngineResult(
        String engine,
        String version,
        String status,
        String errorCode,
        long durationMillis,
        String musicXml,
        Integer measureCount,
        List<MeasureBox> layout
    ) {
        public boolean succeeded() {
            return DONE.equals(status) && musicXml != null;
        }

        public byte[] musicXmlBytes() {
            return Base64.getDecoder().decode(musicXml);
        }
    }

    /** A measure on a page: page from 1, coordinates as fractions of the page size. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeasureBox(int measure, int page, double x, double y, double width, double height, boolean approximate) {}
}
