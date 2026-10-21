package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * A change in the acts of a loan sheet signed electronically, with the users who have to act on it: {@code NoticesAspect}
 * composes the notice. {@code purgeAfter} is the date when Taurus removes the signed acts of a sheet that has ended.
 */
public record LoanSheetActChange(
    Long sheetId,
    String sheetNumber,
    Long actId,
    SignatureActType actType,
    String operation,
    List<String> recipients,
    ZonedDateTime purgeAfter
) {
}
