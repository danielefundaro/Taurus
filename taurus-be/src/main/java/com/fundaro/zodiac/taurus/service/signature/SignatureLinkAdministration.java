package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Act;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Links of the parents managed by the administrators: a new link is created in its own transaction and sent right
 * after, so that a refused e-mail is reported to the administrator, who can try again.
 */
@Service
public class SignatureLinkAdministration {

    private final LoanSheetSignatureService signatureService;
    private final SignatureLinkService linkService;

    public SignatureLinkAdministration(LoanSheetSignatureService signatureService, SignatureLinkService linkService) {
        this.signatureService = signatureService;
        this.linkService = linkService;
    }

    public Act resend(long actId, long signerId, AbstractAuthenticationToken token) {
        SignatureLinkService.PendingLink pending = signatureService.resendLink(actId, signerId, token);
        if (!linkService.deliver(pending)) {
            throw SignatureSupport.error(HttpStatus.BAD_GATEWAY, "The e-mail with the link could not be sent", "signature.link.sendFailed");
        }
        return signatureService.act(actId, token);
    }

    public Act revoke(long actId, long signerId, AbstractAuthenticationToken token) {
        return signatureService.revokeLink(actId, signerId, token);
    }
}
