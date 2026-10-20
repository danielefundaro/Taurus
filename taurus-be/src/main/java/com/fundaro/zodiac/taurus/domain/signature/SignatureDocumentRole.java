package com.fundaro.zodiac.taurus.domain.signature;

/**
 * Role of a frozen document within its act; {@code SNAPSHOT} is the hash of an assignment revision, without a file.
 */
public enum SignatureDocumentRole {
    MAIN,
    CLAUSES,
    SNAPSHOT
}
