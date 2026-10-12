package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.*;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanSheetService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiresTenantFeature(TenantFeature.INVENTORY)
public class InventoryLoanSheetResource {

    private final InventoryLoanSheetService loanSheetService;

    public InventoryLoanSheetResource(InventoryLoanSheetService loanSheetService) {
        this.loanSheetService = loanSheetService;
    }

    @GetMapping("/users/{userIndex}/loan-sheets")
    public List<Summary> getUserSheets(@PathVariable Long userIndex, AbstractAuthenticationToken token) {
        return loanSheetService.findForUser(userIndex, token);
    }

    @GetMapping("/users/{userIndex}/loan-expiration-range")
    public ExpirationRange getExpirationRange(
        @PathVariable Long userIndex,
        @RequestParam(required = false) LocalDate deliveredOn,
        AbstractAuthenticationToken token
    ) {
        return loanSheetService.expirationRange(userIndex, deliveredOn, token);
    }

    @GetMapping("/users/{userIndex}/loan-sheets/proposal")
    public Proposal getProposal(@PathVariable Long userIndex, AbstractAuthenticationToken token) {
        return loanSheetService.proposal(userIndex, token);
    }

    @PostMapping(value = "/users/{userIndex}/loan-sheets/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable Long userIndex, @Valid @RequestBody IssueRequest request, AbstractAuthenticationToken token) {
        return pdfResponse(loanSheetService.previewForUser(userIndex, request, token));
    }

    @PostMapping("/users/{userIndex}/loan-sheets")
    public ResponseEntity<Detail> issue(@PathVariable Long userIndex, @Valid @RequestBody IssueRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(loanSheetService.issue(userIndex, request, token));
    }

    @GetMapping("/loan-sheets/{id}")
    public Detail getSheet(@PathVariable long id, AbstractAuthenticationToken token) {
        return loanSheetService.detail(id, token);
    }

    @GetMapping(value = "/loan-sheets/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getPdf(@PathVariable long id, AbstractAuthenticationToken token) {
        return pdfResponse(loanSheetService.pdf(id, token));
    }

    @PostMapping("/loan-sheets/{id}/assignments/{assignmentId}")
    public Detail linkAssignment(@PathVariable long id, @PathVariable long assignmentId, AbstractAuthenticationToken token) {
        return loanSheetService.linkAssignment(id, assignmentId, token);
    }

    @PostMapping("/loan-sheets/{id}/close")
    public Detail close(@PathVariable long id, @Valid @RequestBody CloseRequest request, AbstractAuthenticationToken token) {
        return loanSheetService.close(id, request, token);
    }

    @PostMapping("/loan-sheets/{id}/settle")
    public Detail settle(@PathVariable long id, AbstractAuthenticationToken token) {
        return loanSheetService.settle(id, token);
    }

    @PostMapping("/loan-sheets/{id}/cancel")
    public Detail cancel(@PathVariable long id, @Valid @RequestBody CancelRequest request, AbstractAuthenticationToken token) {
        return loanSheetService.cancel(id, request, token);
    }

    @PostMapping("/assignments/{id}/renewals")
    public InventoryAssignmentDTO renew(@PathVariable long id, @Valid @RequestBody RenewalRequest request, AbstractAuthenticationToken token) {
        return loanSheetService.renew(id, request, token);
    }

    @PostMapping("/assignments/{id}/substitution")
    public ResponseEntity<InventoryAssignmentDTO> substitute(
        @PathVariable long id,
        @Valid @RequestBody SubstitutionRequest request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.status(201).body(loanSheetService.substitute(id, request, token));
    }

    public static ResponseEntity<byte[]> pdfResponse(PdfContent content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(content.fileName()).build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(content.bytes());
    }
}
