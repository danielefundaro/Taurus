package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;

import java.util.List;

/**
 * What a confirmation code confirms for one purpose: the checks of a new challenge and the signatures recorded when
 * the code is confirmed.
 */
public interface SignaturePurposeHandler {

    SignaturePurpose purpose();

    /**
     * Checks the items of a new challenge for the signer and returns what the code will confirm.
     */
    Prepared prepare(List<ChallengeItem> items, Signer signer);

    /**
     * Describes the items of an existing challenge again, from its stored JSON, for a new code.
     */
    Prepared describe(String itemsJson);

    /**
     * Checks the items again and records the signatures, in the transaction that closes the challenge. An application
     * error closes the challenge: the signer has to request a new code.
     */
    List<ActOutcome> confirm(SignatureChallenge challenge, Signer signer);

    /**
     * Items of a challenge: their canonical JSON, whose hash binds the code, the operation and reference printed in the
     * e-mail, and the declarations shown to the signer.
     */
    record Prepared(String itemsJson, String operation, String reference, List<String> declarations) {
    }
}
