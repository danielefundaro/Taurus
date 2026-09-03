package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AttachmentDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.DashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventBudgetRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.ReconciliationRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.service.impl.FinanceReportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/finance")
public class FinanceResource {

    private final FinanceService financeService;
    private final FinanceReportService financeReportService;

    public FinanceResource(FinanceService financeService, FinanceReportService financeReportService) {
        this.financeService = financeService;
        this.financeReportService = financeReportService;
    }

    @GetMapping("/accounts")
    public List<AccountDTO> accounts(@RequestParam(defaultValue = "false") boolean includeArchived, AbstractAuthenticationToken token) {
        return financeService.findAccounts(includeArchived, token);
    }

    @GetMapping("/accounts/{id}")
    public AccountDTO account(
        @PathVariable long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        AbstractAuthenticationToken token
    ) {
        return financeService.findAccount(id, date, token);
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountDTO> createAccount(@Valid @RequestBody AccountRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(financeService.createAccount(request, token));
    }

    @PutMapping("/accounts/{id}")
    public AccountDTO updateAccount(@PathVariable long id, @Valid @RequestBody AccountRequest request, AbstractAuthenticationToken token) {
        return financeService.updateAccount(id, request, token);
    }

    @PatchMapping("/accounts/{id}/archive")
    public ResponseEntity<Void> archiveAccount(@PathVariable long id, AbstractAuthenticationToken token) {
        financeService.archiveAccount(id, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/accounts/{id}/balance")
    public BigDecimal balance(
        @PathVariable long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        AbstractAuthenticationToken token
    ) {
        return financeService.accountBalance(id, date, token);
    }

    @GetMapping("/accounts/{id}/statement")
    public AccountStatementDTO statement(
        @PathVariable long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        AbstractAuthenticationToken token
    ) {
        return financeService.accountStatement(id, from, to, token);
    }

    @GetMapping("/categories")
    public List<CategoryDTO> categories(@RequestParam(defaultValue = "false") boolean includeArchived, AbstractAuthenticationToken token) {
        return financeService.findCategories(includeArchived, token);
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryDTO> createCategory(@Valid @RequestBody CategoryRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(financeService.createCategory(request, token));
    }

    @PutMapping("/categories/{id}")
    public CategoryDTO updateCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest request, AbstractAuthenticationToken token) {
        return financeService.updateCategory(id, request, token);
    }

    @PatchMapping("/categories/{id}/archive")
    public ResponseEntity<Void> archiveCategory(@PathVariable long id, AbstractAuthenticationToken token) {
        financeService.archiveCategory(id, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/movements")
    public Page<MovementDTO> movements(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long eventId,
        @RequestParam(required = false) FinancialDirection direction,
        @RequestParam(required = false) Boolean reconciled,
        @RequestParam(required = false) String query,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        return financeService.findMovements(from, to, accountId, categoryId, eventId, direction, reconciled, query, pageable, token);
    }

    @GetMapping("/movements/{id}")
    public MovementDTO movement(@PathVariable long id, AbstractAuthenticationToken token) {
        return financeService.findMovement(id, token);
    }

    @PostMapping("/movements")
    public ResponseEntity<MovementDTO> createMovement(@Valid @RequestBody MovementRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(financeService.createMovement(request, token));
    }

    @PutMapping("/movements/{id}")
    public MovementDTO updateMovement(@PathVariable long id, @Valid @RequestBody MovementRequest request, AbstractAuthenticationToken token) {
        return financeService.updateMovement(id, request, token);
    }

    @DeleteMapping("/movements/{id}")
    public ResponseEntity<Void> deleteMovement(@PathVariable long id, AbstractAuthenticationToken token) {
        financeService.deleteMovement(id, token);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/movements/{id}/reconciliation")
    public MovementDTO reconcile(
        @PathVariable long id,
        @Valid @RequestBody ReconciliationRequest request,
        AbstractAuthenticationToken token
    ) {
        return financeService.reconcile(id, request, token);
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferDTO> transfer(@Valid @RequestBody TransferRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(financeService.createTransfer(request, token));
    }

    @GetMapping("/dashboard")
    public DashboardDTO dashboard(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        AbstractAuthenticationToken token
    ) {
        return financeService.dashboard(from, to, token);
    }

    @GetMapping("/reports/cashbook")
    public ResponseEntity<byte[]> cashbook(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) Long accountId,
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) Long eventId,
        @RequestParam(defaultValue = "csv") String format,
        AbstractAuthenticationToken token
    ) {
        return download(financeReportService.cashbook(from, to, accountId, categoryId, eventId, format, token));
    }

    @GetMapping("/reports/account-statement")
    public ResponseEntity<byte[]> accountStatementReport(
        @RequestParam long accountId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "csv") String format,
        AbstractAuthenticationToken token
    ) {
        return download(financeReportService.accountStatement(accountId, from, to, format, token));
    }

    @GetMapping("/reports/events")
    public ResponseEntity<byte[]> eventsReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "csv") String format,
        AbstractAuthenticationToken token
    ) {
        return download(financeReportService.events(from, to, format, token));
    }

    @GetMapping("/reports/categories")
    public ResponseEntity<byte[]> categoriesReport(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "csv") String format,
        AbstractAuthenticationToken token
    ) {
        return download(financeReportService.categories(from, to, format, token));
    }

    @GetMapping("/reports/annual")
    public ResponseEntity<byte[]> annualReport(
        @RequestParam int year,
        @RequestParam(defaultValue = "csv") String format,
        AbstractAuthenticationToken token
    ) {
        return download(financeReportService.annual(year, format, token));
    }

    @PostMapping(value = "/movements/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDTO> addAttachment(
        @PathVariable long id,
        @RequestPart("file") MultipartFile file,
        @RequestPart(value = "description", required = false) String description,
        AbstractAuthenticationToken token
    ) throws IOException {
        return ResponseEntity.status(201).body(financeService.addAttachment(id, file, description, token));
    }

    @GetMapping("/movements/{id}/attachments")
    public List<AttachmentDTO> attachments(@PathVariable long id, AbstractAuthenticationToken token) {
        return financeService.findAttachments(id, token);
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> attachment(@PathVariable long id, AbstractAuthenticationToken token) {
        MediaService.MediaContent content = financeService.getAttachment(id, token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(content.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(content.bytes());
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<Void> deleteAttachment(@PathVariable long id, AbstractAuthenticationToken token) {
        financeService.deleteAttachment(id, token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public Page<EventSummaryDTO> events(Pageable pageable, AbstractAuthenticationToken token) {
        return financeService.findEvents(pageable, token);
    }

    @GetMapping("/events/{id}")
    public EventSummaryDTO event(@PathVariable long id, AbstractAuthenticationToken token) {
        return financeService.eventSummary(id, token);
    }

    @PatchMapping("/events/{id}/budget")
    public EventSummaryDTO updateBudget(
        @PathVariable long id,
        @Valid @RequestBody EventBudgetRequest request,
        AbstractAuthenticationToken token
    ) {
        return financeService.updateEventBudget(id, request, token);
    }

    @GetMapping("/events/{id}/movements")
    public List<MovementDTO> eventMovements(@PathVariable long id, AbstractAuthenticationToken token) {
        return financeService.eventSummary(id, token).movements();
    }

    @PostMapping("/events/{id}/movements")
    public ResponseEntity<MovementDTO> createEventMovement(
        @PathVariable long id,
        @Valid @RequestBody MovementRequest request,
        AbstractAuthenticationToken token
    ) {
        MovementRequest linked = new MovementRequest(
            request.accountId(), request.categoryId(), id, request.direction(), request.bookingDate(), request.valueDate(), request.amount(),
            request.description(), request.counterparty(), request.documentReference(), request.notes(), request.requestKey()
        );
        return ResponseEntity.status(201).body(financeService.createMovement(linked, token));
    }

    @GetMapping("/years")
    public List<YearDTO> years(AbstractAuthenticationToken token) {
        return financeService.findYears(token);
    }

    @GetMapping("/years/{year}")
    public YearDTO year(@PathVariable int year, AbstractAuthenticationToken token) {
        return financeService.findYear(year, token);
    }

    @GetMapping("/years/{year}/summary")
    public YearSummaryDTO yearSummary(@PathVariable int year, AbstractAuthenticationToken token) {
        return financeService.yearSummary(year, token);
    }

    @PostMapping("/years/{year}/rollover")
    public YearDTO rollover(@PathVariable int year, AbstractAuthenticationToken token) {
        return financeService.rollover(year, token);
    }

    @PostMapping("/years/{year}/recalculate")
    public YearDTO recalculate(@PathVariable int year, AbstractAuthenticationToken token) {
        return financeService.recalculate(year, token);
    }

    private static ResponseEntity<byte[]> download(FinanceReportService.ReportContent report) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(report.mimeType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(report.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(report.bytes());
    }
}
