package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.*;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/inventory/loan-template")
@RequiresTenantFeature(TenantFeature.INVENTORY_LOAN_SHEET)
public class InventoryLoanTemplateResource {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);

    private final InventoryLoanTemplateService templateService;

    public InventoryLoanTemplateResource(InventoryLoanTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public Overview getOverview(AbstractAuthenticationToken token) {
        return templateService.overview(token);
    }

    @PutMapping("/durations")
    public Overview updateDurations(@Valid @RequestBody DurationsUpdate request, AbstractAuthenticationToken token) {
        return templateService.updateDurations(request, token);
    }

    @PutMapping("/signature")
    @RequiresTenantFeature(TenantFeature.INVENTORY_SIGNATURE)
    public Overview updateSignature(@Valid @RequestBody SignatureDtos.SettingsUpdate request, AbstractAuthenticationToken token) {
        return templateService.updateSignature(request, token);
    }

    @PostMapping("/check")
    public CheckResult check(@Valid @RequestBody TemplateFile file, AbstractAuthenticationToken token) {
        return templateService.check(file, token);
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> preview(@Valid @RequestBody TemplateFile file, AbstractAuthenticationToken token) {
        return InventoryLoanSheetResource.pdfResponse(templateService.preview(file, token));
    }

    @PostMapping
    public Overview activate(@Valid @RequestBody TemplateFile file, AbstractAuthenticationToken token) {
        return templateService.activate(file, token);
    }

    @PostMapping("/versions/{id}/activate")
    public Overview activateVersion(@PathVariable long id, AbstractAuthenticationToken token) {
        return templateService.activateVersion(id, token);
    }

    @PostMapping("/use-default")
    public Overview useDefault(AbstractAuthenticationToken token) {
        return templateService.useDefault(token);
    }

    @GetMapping("/default/file")
    public ResponseEntity<byte[]> downloadDefault(AbstractAuthenticationToken token) {
        return htmlDownload(templateService.defaultFile(token));
    }

    @GetMapping(value = "/default/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> previewDefault(AbstractAuthenticationToken token) {
        return InventoryLoanSheetResource.pdfResponse(templateService.defaultPreview(token));
    }

    @GetMapping("/versions/{id}/file")
    public ResponseEntity<byte[]> downloadVersion(@PathVariable long id, AbstractAuthenticationToken token) {
        return htmlDownload(templateService.versionFile(id, token));
    }

    @GetMapping(value = "/versions/{id}/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> previewVersion(@PathVariable long id, AbstractAuthenticationToken token) {
        return InventoryLoanSheetResource.pdfResponse(templateService.versionPreview(id, token));
    }

    /**
     * A template is HTML written by the tenant: it is only ever downloaded, never rendered in the Taurus origin.
     */
    static ResponseEntity<byte[]> htmlDownload(TemplateDownload download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(HTML);
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "sandbox");
        return ResponseEntity.ok().headers(headers).body(download.content().getBytes(StandardCharsets.UTF_8));
    }
}
