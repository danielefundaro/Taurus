package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Summary;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanSheetService;
import com.fundaro.zodiac.taurus.web.rest.InventoryLoanSheetResource;
import com.fundaro.zodiac.taurus.web.rest.RequiresTenantFeature;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user/inventory/loan-sheets")
@RequiresTenantFeature(TenantFeature.INVENTORY_LOAN_SHEET)
public class UserInventoryLoanSheetResource {

    private final InventoryLoanSheetService loanSheetService;

    public UserInventoryLoanSheetResource(InventoryLoanSheetService loanSheetService) {
        this.loanSheetService = loanSheetService;
    }

    @GetMapping
    public List<Summary> getOwnSheets(AbstractAuthenticationToken token) {
        return loanSheetService.findOwn(token);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getOwnPdf(@PathVariable long id, AbstractAuthenticationToken token) {
        return InventoryLoanSheetResource.pdfResponse(loanSheetService.ownPdf(id, token));
    }
}
