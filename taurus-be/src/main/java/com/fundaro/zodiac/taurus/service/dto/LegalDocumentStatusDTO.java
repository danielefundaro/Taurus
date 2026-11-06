package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;

import java.time.ZonedDateTime;

/**
 * A version in force and what the user did with it. {@code previousVersionAccepted} tells whether the user had
 * confirmed another version of the same document in the tenant; {@code url} is set only for a document published
 * before the text lived in Taurus.
 */
public record LegalDocumentStatusDTO(
    Long id,
    LegalDocumentType documentType,
    String version,
    String title,
    String url,
    String contentSha256,
    String changeSummary,
    LegalDocumentAction action,
    ZonedDateTime publishedAt,
    boolean required,
    boolean accepted,
    ZonedDateTime acceptedAt,
    boolean previousVersionAccepted
) {
}
