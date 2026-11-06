package com.fundaro.zodiac.taurus.domain.enumeration;

import com.fundaro.zodiac.taurus.domain.LegalDocument;

/**
 * Where a version of a legal document stands: only a draft changes, a version in force or retired is public and
 * immutable.
 */
public enum LegalDocumentState {
    DRAFT,
    ACTIVE,
    RETIRED;

    public static LegalDocumentState of(LegalDocument document) {
        if (document.isDraft()) return DRAFT;
        return Boolean.TRUE.equals(document.getActive()) ? ACTIVE : RETIRED;
    }
}
