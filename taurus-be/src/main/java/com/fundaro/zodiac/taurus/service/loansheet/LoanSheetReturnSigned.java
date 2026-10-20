package com.fundaro.zodiac.taurus.service.loansheet;

/**
 * The return report of a sheet signed electronically, or declared signed on paper: a return without charges settles
 * the sheet at once, instead of the manual confirmation of the signed report.
 */
public record LoanSheetReturnSigned(Long sheetId, String tenantCode, String actor) {
}
