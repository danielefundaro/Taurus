package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetVariationRecorded;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the act of every variation of a sheet signed electronically, in the transaction of the operation.
 */
@Component
public class LoanSheetSignatureEvents {

    private final LoanSheetSignatureService signatureService;

    public LoanSheetSignatureEvents(LoanSheetSignatureService signatureService) {
        this.signatureService = signatureService;
    }

    @EventListener
    public void variationRecorded(LoanSheetVariationRecorded event) {
        signatureService.createVariationAct(event);
    }
}
