package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * A published version of a legal document as anyone reads it, without login: the text as HTML, its fingerprint and
 * the other published versions of the same document. No author, tenant or personal data.
 */
public record PublicLegalDocumentDTO(
    LegalDocumentType documentType,
    String version,
    String title,
    LegalDocumentAction action,
    ZonedDateTime publishedAt,
    boolean active,
    String changeSummary,
    String html,
    String contentSha256,
    String url,
    List<Version> versions
) {
    public record Version(String version, String title, ZonedDateTime publishedAt, boolean active) {
    }
}
