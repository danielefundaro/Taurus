package com.fundaro.zodiac.taurus.service.signature;

import java.time.ZonedDateTime;

/**
 * A valid signature link of the current request: the tenant resolved by the public registry, the link and the signer
 * of the act it opens. It is never stored in the security context.
 */
public record SignatureLinkSession(
    String tenantCode,
    Long linkId,
    Long signerId,
    Long actId,
    String fingerprint,
    ZonedDateTime expiresAt
) {
    /**
     * Who acts through the link, in the audit fields: the link, never the address of the parent.
     */
    public String actor() {
        return actor(linkId);
    }

    static String actor(Long linkId) {
        return "signature-link-" + linkId;
    }
}
