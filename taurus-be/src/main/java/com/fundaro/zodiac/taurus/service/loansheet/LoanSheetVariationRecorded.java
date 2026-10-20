package com.fundaro.zodiac.taurus.service.loansheet;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * A variation of an issued sheet entered in the register of section 8: a later delivery or a substitution
 * ({@code assignmentId}), a renewal ({@code renewalId}) or a partial return ({@code returnId}). Published in the
 * transaction of the operation, so that a sheet signed electronically gets the act of the variation with it.
 */
public record LoanSheetVariationRecorded(Long sheetId, Long assignmentId, Long renewalId, Long returnId, AbstractAuthenticationToken token) {
    public static LoanSheetVariationRecorded delivery(Long sheetId, Long assignmentId, AbstractAuthenticationToken token) {
        return new LoanSheetVariationRecorded(sheetId, assignmentId, null, null, token);
    }

    public static LoanSheetVariationRecorded renewal(Long sheetId, Long renewalId, AbstractAuthenticationToken token) {
        return new LoanSheetVariationRecorded(sheetId, null, renewalId, null, token);
    }

    public static LoanSheetVariationRecorded partialReturn(Long sheetId, Long returnId, AbstractAuthenticationToken token) {
        return new LoanSheetVariationRecorded(sheetId, null, null, returnId, token);
    }

    /**
     * Origin of the rows of the variation in the register, as returned by {@link LoanSheetRegisterBuilder}.
     */
    public String registerKey() {
        if (assignmentId != null) return LoanSheetRegisterBuilder.assignmentKey(assignmentId);
        if (renewalId != null) return LoanSheetRegisterBuilder.renewalKey(renewalId);
        return LoanSheetRegisterBuilder.returnKey(returnId);
    }

    @Override
    public String toString() {
        return "LoanSheetVariationRecorded[sheetId=" + sheetId + ", key=" + registerKey() + "]";
    }
}
