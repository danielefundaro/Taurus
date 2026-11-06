package com.fundaro.zodiac.taurus.service.dto;

/**
 * The text of a legal document and the HTML the public page shows for it.
 */
public final class LegalPreviewDTO {

    private LegalPreviewDTO() {
    }

    public record Request(String content) {
    }

    public record Response(String html) {
    }
}
