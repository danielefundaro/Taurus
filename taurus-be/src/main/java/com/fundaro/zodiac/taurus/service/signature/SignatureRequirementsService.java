package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Requirements;
import org.springframework.stereotype.Service;

/**
 * What the installation must provide before a tenant can enable the electronic signature: an SMTP server, the secret
 * of the confirmation codes and a seal certificate.
 */
@Service
public class SignatureRequirementsService {

    private final SignatureMailService mailService;
    private final SignatureCodes codes;
    private final SignatureSealService sealService;

    public SignatureRequirementsService(SignatureMailService mailService, SignatureCodes codes, SignatureSealService sealService) {
        this.mailService = mailService;
        this.codes = codes;
        this.sealService = sealService;
    }

    public Requirements requirements() {
        return new Requirements(mailService.configured(), codes.configured(), sealService.available());
    }
}
