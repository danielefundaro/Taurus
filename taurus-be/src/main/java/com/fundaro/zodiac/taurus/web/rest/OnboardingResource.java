package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.service.dto.onboarding.OnboardingDtos;
import com.fundaro.zodiac.taurus.service.onboarding.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/onboarding")
public class OnboardingResource {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final OnboardingImportService imports;
    private final OnboardingTemplateService templates;
    private final OnboardingReportService reports;
    public OnboardingResource(OnboardingImportService imports, OnboardingTemplateService templates, OnboardingReportService reports) { this.imports = imports; this.templates = templates; this.reports = reports; }

    @GetMapping("/context") public OnboardingDtos.Context context(AbstractAuthenticationToken token) { return imports.context(token); }
    @GetMapping("/templates/xlsx") public ResponseEntity<byte[]> xlsx(AbstractAuthenticationToken token) { imports.context(token); return download(templates.xlsx(), XLSX, "taurus-onboarding-v1.xlsx"); }
    @GetMapping("/templates/csv") public ResponseEntity<byte[]> csv(@RequestParam OnboardingSection section, AbstractAuthenticationToken token) { imports.context(token); return download(templates.csv(section), MediaType.parseMediaType("text/csv;charset=UTF-8"), "taurus-onboarding-v1-" + section.name().toLowerCase() + ".csv"); }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OnboardingDtos.Job> upload(@RequestPart("file") MultipartFile file, @RequestParam OnboardingImportFormat format,
        @RequestParam(required = false) OnboardingSection csvSection, @RequestParam(required = false) Set<OnboardingSection> selectedSections,
        @RequestHeader("Idempotency-Key") UUID key, AbstractAuthenticationToken token) {
        OnboardingDtos.Job job = imports.upload(file, format, csvSection, selectedSections, key, token);
        return ResponseEntity.accepted().location(URI.create("/api/onboarding/imports/" + job.id())).body(job);
    }
    @GetMapping("/imports") public Page<OnboardingDtos.Job> list(@PageableDefault(size = 20, sort = "insertDate", direction = Sort.Direction.DESC) Pageable pageable, AbstractAuthenticationToken token) { return imports.list(pageable, token); }
    @GetMapping("/imports/{id}") public ResponseEntity<OnboardingDtos.Job> get(@PathVariable Long id, AbstractAuthenticationToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.get(id, token)); }
    @DeleteMapping("/imports/{id}") public ResponseEntity<Void> cancel(@PathVariable Long id, AbstractAuthenticationToken token) { imports.cancel(id, token); return ResponseEntity.noContent().build(); }
    @PostMapping("/imports/{id}/retry-validation") public ResponseEntity<OnboardingDtos.Job> retryValidation(@PathVariable Long id, AbstractAuthenticationToken token) { return ResponseEntity.accepted().body(imports.retryValidation(id, token)); }
    @PostMapping("/imports/{id}/apply") public ResponseEntity<OnboardingDtos.Job> apply(@PathVariable Long id, @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody OnboardingDtos.ApplyRequest request, AbstractAuthenticationToken token) { return ResponseEntity.accepted().body(imports.apply(id, key, request, token)); }
    @PostMapping("/imports/{id}/retry-compensation") public OnboardingDtos.Job retryCompensation(@PathVariable Long id, AbstractAuthenticationToken token) { return imports.retryCompensation(id, token); }
    @PostMapping("/imports/{id}/retry-setup-emails") public OnboardingDtos.Job retryEmails(@PathVariable Long id, AbstractAuthenticationToken token) { return imports.retryEmails(id, token); }
    @GetMapping("/imports/{id}/sections") public ResponseEntity<?> sections(@PathVariable Long id, AbstractAuthenticationToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.sections(id, token)); }
    @GetMapping("/imports/{id}/rows") public ResponseEntity<?> rows(@PathVariable Long id, @RequestParam(required = false) OnboardingSection section, @RequestParam(required = false) OnboardingRowStatus status, Pageable pageable, AbstractAuthenticationToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.rows(id, section, status, pageable, token)); }
    @GetMapping("/imports/{id}/issues") public ResponseEntity<?> issues(@PathVariable Long id, @RequestParam(required = false) OnboardingIssueSeverity severity, @RequestParam(required = false) OnboardingSection section, Pageable pageable, AbstractAuthenticationToken token) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.issues(id, severity, section, pageable, token)); }
    @GetMapping({"/imports/{id}/validation-report", "/imports/{id}/final-report"}) public ResponseEntity<byte[]> report(@PathVariable Long id, AbstractAuthenticationToken token) { imports.get(id, token); return download(reports.report(id), XLSX, "taurus-onboarding-report-" + id + ".xlsx"); }

    private ResponseEntity<byte[]> download(byte[] content, MediaType type, String name) { return ResponseEntity.ok().contentType(type).contentLength(content.length).cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString()).header("X-Content-Type-Options", "nosniff").body(content); }
}
