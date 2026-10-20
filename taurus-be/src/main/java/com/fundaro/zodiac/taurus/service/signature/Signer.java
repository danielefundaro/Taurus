package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Who confirms a code, with the request data recorded in the evidence. An authenticated user is identified by
 * {@code userId}, always the subject of the token and never a value sent by the client; a parent or guardian without an
 * account by {@code signerId}, the signer row of the link, and has no {@code token}. {@code actor} fills the audit
 * fields.
 */
public record Signer(
    String userId,
    Long signerId,
    String actor,
    String displayName,
    String email,
    String sessionId,
    String ipAddress,
    String userAgent,
    String tenantCode,
    AbstractAuthenticationToken token
) {
    /**
     * Whether this is the given signer of an act: by the account or, through a link, by the signer row.
     */
    public boolean is(SignatureActSigner signer) {
        if (signerId != null) return signerId.equals(signer.getId()) && signer.getUserKeycloakId() == null;
        return userId != null && userId.equals(signer.getUserKeycloakId());
    }

    @Override
    public String toString() {
        return "Signer[userId=" + userId + ", signerId=" + signerId + "]";
    }
}
