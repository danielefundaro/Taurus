package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;

import java.time.ZonedDateTime;

public record LegalDocumentStatusDTO(
    Long id,
    LegalDocumentType documentType,
    String version,
    String title,
    String url,
    LegalDocumentAction action,
    ZonedDateTime publishedAt,
    boolean required,
    boolean accepted,
    ZonedDateTime acceptedAt
) {
}
