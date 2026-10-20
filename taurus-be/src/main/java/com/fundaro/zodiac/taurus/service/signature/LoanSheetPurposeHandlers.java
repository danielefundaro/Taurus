package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The purposes of the signatures of a loan sheet, each handled by {@link LoanSheetSigning}.
 */
public final class LoanSheetPurposeHandlers {

    private LoanSheetPurposeHandlers() {
    }

    abstract static class Base implements SignaturePurposeHandler {

        private final SignaturePurpose purpose;
        private final LoanSheetSigning signing;

        Base(SignaturePurpose purpose, LoanSheetSigning signing) {
            this.purpose = purpose;
            this.signing = signing;
        }

        @Override
        public SignaturePurpose purpose() {
            return purpose;
        }

        @Override
        public Prepared prepare(List<ChallengeItem> items, Signer signer) {
            return signing.prepare(purpose, items, signer);
        }

        @Override
        public Prepared describe(String itemsJson) {
            return signing.describe(purpose, itemsJson);
        }

        @Override
        public List<ActOutcome> confirm(SignatureChallenge challenge, Signer signer) {
            return signing.confirm(purpose, challenge, signer);
        }
    }

    /**
     * Acceptance by the holder of the sheet, of a variation or of the return report.
     */
    @Component
    public static class Acceptance extends Base {

        public Acceptance(LoanSheetSigning signing) {
            super(SignaturePurpose.ACCEPTANCE, signing);
        }
    }

    /**
     * Approval of the clauses by the holder, a signature distinct from the acceptance of the issue.
     */
    @Component
    public static class ClauseApproval extends Base {

        public ClauseApproval(LoanSheetSigning signing) {
            super(SignaturePurpose.CLAUSE_APPROVAL, signing);
        }
    }

    /**
     * Acknowledgment of the issue by the minor member, from the own account, when the holder is a parent or guardian:
     * required and notified, it does not block the completion of the act.
     */
    @Component
    public static class MinorAcknowledgment extends Base {

        public MinorAcknowledgment(LoanSheetSigning signing) {
            super(SignaturePurpose.MINOR_ACKNOWLEDGMENT, signing);
        }
    }

    /**
     * Signature of the administrator designated for the association.
     */
    @Component
    public static class AssociationSignature extends Base {

        public AssociationSignature(LoanSheetSigning signing) {
            super(SignaturePurpose.ASSOCIATION_SIGNATURE, signing);
        }
    }
}
