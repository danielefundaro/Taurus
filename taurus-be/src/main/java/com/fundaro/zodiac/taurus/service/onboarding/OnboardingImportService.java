package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.*;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.finance.FinancialAccountRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.onboarding.OnboardingDtos;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OnboardingImportService {
    private static final String ENTITY = "onboardingImport";
    private final ApplicationProperties.OnboardingProperties properties;
    private final OnboardingImportJobRepository jobs;
    private final OnboardingImportSectionRepository sections;
    private final OnboardingImportRowRepository rows;
    private final OnboardingImportIssueRepository issues;
    private final MediaRepository media;
    private final MediaService mediaService;
    private final TenantsRepository tenants;
    private final TenantSchemaRegistry schemas;
    private final UsersRepository users;
    private final InstrumentsRepository instruments;
    private final InventoryItemRepository inventory;
    private final FinancialAccountRepository accounts;
    private final ApplicationEventPublisher events;
    private final OnboardingIdentitySagaService identitySaga;

    public OnboardingImportService(ApplicationProperties applicationProperties, OnboardingImportJobRepository jobs,
        OnboardingImportSectionRepository sections, OnboardingImportRowRepository rows, OnboardingImportIssueRepository issues,
        MediaRepository media, MediaService mediaService, TenantsRepository tenants, TenantSchemaRegistry schemas,
        UsersRepository users, InstrumentsRepository instruments, InventoryItemRepository inventory, FinancialAccountRepository accounts,
        ApplicationEventPublisher events, OnboardingIdentitySagaService identitySaga) {
        this.properties = applicationProperties.getOnboarding(); this.jobs = jobs; this.sections = sections; this.rows = rows; this.issues = issues;
        this.media = media; this.mediaService = mediaService; this.tenants = tenants; this.schemas = schemas; this.users = users; this.instruments = instruments;
        this.inventory = inventory; this.accounts = accounts; this.events = events; this.identitySaga = identitySaga;
    }

    @Transactional(readOnly = true)
    public OnboardingDtos.Context context(AbstractAuthenticationToken token) {
        Tenant tenant = tenant(token);
        OnboardingDtos.Job last = jobs.findFirstByStatusOrderByCompletedAtDesc(OnboardingJobStatus.COMPLETED).map(this::dto).orElse(null);
        return new OnboardingDtos.Context(tenant.code, tenant.entity.getName(), true, tenant.entity.getMaxUsers(), activeUsers(), activeInstruments(), inventory.countByDeletedFalse(), accounts.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().size(), List.of(1), last,
            new OnboardingDtos.Limits(properties.getMaxFileSize().toBytes(), properties.getMaxTotalRows(), properties.getMaxUserRows()));
    }

    @Transactional
    public OnboardingDtos.Job upload(MultipartFile file, OnboardingImportFormat format, OnboardingSection csvSection,
        Set<OnboardingSection> selectedSections, UUID key, AbstractAuthenticationToken token) {
        requireEnabled(); Tenant tenant = tenant(token); String actor = actor(token);
        OnboardingImportJob existing = jobs.findByRequestedByAndUploadIdempotencyKeyAndDeletedFalse(actor, key).orElse(null); if (existing != null) return dto(existing);
        if (file == null || file.isEmpty()) throw problem(HttpStatus.BAD_REQUEST, "Il file è obbligatorio", "file.required");
        if (file.getSize() > properties.getMaxFileSize().toBytes()) throw problem(HttpStatus.PAYLOAD_TOO_LARGE, "File troppo grande", "file.tooLarge");
        if (format == OnboardingImportFormat.CSV && csvSection == null) throw problem(HttpStatus.BAD_REQUEST, "La sezione CSV è obbligatoria", "csvSection.required");
        if (format == OnboardingImportFormat.XLSX && (selectedSections == null || selectedSections.isEmpty())) throw problem(HttpStatus.BAD_REQUEST, "Selezionare almeno una sezione", "selectedSections.required");
        try {
            byte[] content = file.getBytes(); MediaDTO stored = mediaService.store(content, file.getOriginalFilename(), file.getContentType(), "onboarding-imports", token);
            OnboardingImportJob job = new OnboardingImportJob(); job.initializeAudit(actor); job.setSourceMediaAsset(media.getReferenceById(stored.getId())); job.setFileName(stored.getOriginalFilename()); job.setFileSha256(stored.getSha256()); job.setFormat(format); job.setCsvSection(csvSection);
            job.setSelectedSections(selectedSections == null ? null : selectedSections.stream().sorted().map(Enum::name).reduce((a,b) -> a + "," + b).orElse(null)); job.setStatus(OnboardingJobStatus.UPLOADED); job.setStage("QUEUED_FOR_VALIDATION"); job.setUploadIdempotencyKey(key); job.setRequestedBy(actor); job = jobs.save(job);
            events.publishEvent(new OnboardingWorkEvents.Validate(job.getId(), content, tenant.code)); return dto(job);
        } catch (IOException exception) { throw problem(HttpStatus.BAD_REQUEST, "Impossibile leggere il file", "file.read"); }
    }

    @Transactional(readOnly = true) public Page<OnboardingDtos.Job> list(Pageable pageable, AbstractAuthenticationToken token) { tenant(token); return jobs.findAllByDeletedFalse(normalize(pageable)).map(this::dto); }
    @Transactional(readOnly = true) public OnboardingDtos.Job get(Long id, AbstractAuthenticationToken token) { tenant(token); return dto(required(id)); }
    @Transactional(readOnly = true) public List<OnboardingDtos.Section> sections(Long id, AbstractAuthenticationToken token) { tenant(token); required(id); return sections.findAllByJob_IdOrderBySectionAsc(id).stream().map(this::dto).toList(); }
    @Transactional(readOnly = true) public Page<OnboardingDtos.Row> rows(Long id, OnboardingSection section, OnboardingRowStatus status, Pageable pageable, AbstractAuthenticationToken token) {
        tenant(token); required(id); Pageable safe = normalize(pageable); Page<OnboardingImportRow> result = section != null && status != null ? rows.findAllByJob_IdAndSectionAndStatus(id, section, status, safe) : section != null ? rows.findAllByJob_IdAndSection(id, section, safe) : status != null ? rows.findAllByJob_IdAndStatus(id, status, safe) : rows.findAllByJob_Id(id, safe); return result.map(this::dto);
    }
    @Transactional(readOnly = true) public Page<OnboardingDtos.Issue> issues(Long id, OnboardingIssueSeverity severity, OnboardingSection section, Pageable pageable, AbstractAuthenticationToken token) {
        tenant(token); required(id); Pageable safe = normalize(pageable); Page<OnboardingImportIssue> result = severity != null && section != null ? issues.findAllByJob_IdAndSeverityAndSection(id, severity, section, safe) : severity != null ? issues.findAllByJob_IdAndSeverity(id, severity, safe) : section != null ? issues.findAllByJob_IdAndSection(id, section, safe) : issues.findAllByJob_Id(id, safe); return result.map(this::dto);
    }

    @Transactional
    public OnboardingDtos.Job apply(Long id, UUID key, OnboardingDtos.ApplyRequest request, AbstractAuthenticationToken token) {
        Tenant tenant = tenant(token); String actor = actor(token); OnboardingImportJob job = jobs.findForUpdate(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Importazione non trovata", "id.notFound"));
        if (job.getStatus() == OnboardingJobStatus.COMPLETED || (job.getStatus() == OnboardingJobStatus.APPLYING && key.equals(job.getApplyIdempotencyKey()))) return dto(job);
        if (job.getStatus() != OnboardingJobStatus.READY) throw problem(HttpStatus.CONFLICT, "L'importazione non è pronta", "status.conflict");
        if (job.getApplyIdempotencyKey() != null && !key.equals(job.getApplyIdempotencyKey())) throw problem(HttpStatus.CONFLICT, "Chiave di applicazione differente", "idempotency.conflict");
        if (job.getWarningRows() > 0 && !Boolean.TRUE.equals(request.warningsAccepted())) throw problem(HttpStatus.CONFLICT, "È necessario accettare gli avvisi", "warnings.notAccepted");
        if (jobs.existsByStatus(OnboardingJobStatus.APPLYING)) throw problem(HttpStatus.CONFLICT, "Un'altra importazione è già in corso", "tenant.busy");
        job.setApplyIdempotencyKey(key); job.setSendSetupEmails(Boolean.TRUE.equals(request.sendSetupEmails())); job.setWarningsAcceptedAt(Boolean.TRUE.equals(request.warningsAccepted()) ? ZonedDateTime.now() : null); job.setExecutedBy(actor); job.setStatus(OnboardingJobStatus.APPLYING); job.setStage("PREPARING_IDENTITIES"); job.setProgressPercentage(5); jobs.save(job);
        events.publishEvent(new OnboardingWorkEvents.Apply(id, tenant.code, actor)); return dto(job);
    }

    @Transactional public void cancel(Long id, AbstractAuthenticationToken token) { tenant(token); OnboardingImportJob job = jobs.findForUpdate(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Importazione non trovata", "id.notFound")); if (!Set.of(OnboardingJobStatus.UPLOADED, OnboardingJobStatus.INVALID, OnboardingJobStatus.READY).contains(job.getStatus())) throw problem(HttpStatus.CONFLICT, "Il job non può essere annullato", "status.conflict"); job.setStatus(OnboardingJobStatus.CANCELLED); job.setStage("CANCELLED"); job.setCompletedAt(ZonedDateTime.now()); jobs.save(job); }
    @Transactional public OnboardingDtos.Job retryValidation(Long id, AbstractAuthenticationToken token) { Tenant tenant = tenant(token); OnboardingImportJob job = jobs.findForUpdate(id).orElseThrow(); if (!Set.of(OnboardingJobStatus.INVALID, OnboardingJobStatus.FAILED).contains(job.getStatus())) throw problem(HttpStatus.CONFLICT, "Validazione non ripetibile", "status.conflict"); MediaService.MediaContent content = mediaService.getContent(job.getSourceMediaAsset().getId(), token); job.setStatus(OnboardingJobStatus.UPLOADED); job.setStage("QUEUED_FOR_VALIDATION"); jobs.save(job); events.publishEvent(new OnboardingWorkEvents.Validate(id, content.bytes(), tenant.code)); return dto(job); }
    @Transactional public OnboardingDtos.Job retryCompensation(Long id, AbstractAuthenticationToken token) { Tenant tenant = tenant(token); OnboardingImportJob job = jobs.findForUpdate(id).orElseThrow(); if (job.getStatus() != OnboardingJobStatus.COMPENSATION_REQUIRED) throw problem(HttpStatus.CONFLICT, "Ripristino non richiesto", "status.conflict"); boolean ok = identitySaga.compensate(id, tenant.code); job.setStatus(ok ? OnboardingJobStatus.FAILED : OnboardingJobStatus.COMPENSATION_REQUIRED); job.setStage(ok ? "COMPENSATED" : "COMPENSATION_REQUIRED"); return dto(jobs.save(job)); }
    @Transactional public OnboardingDtos.Job retryEmails(Long id, AbstractAuthenticationToken token) { tenant(token); OnboardingImportJob job = jobs.findForUpdate(id).orElseThrow(); if (job.getStatus() != OnboardingJobStatus.COMPLETED || job.getSetupEmailFailures() == 0) throw problem(HttpStatus.CONFLICT, "Nessuna e-mail da ritentare", "email.retryNotAllowed"); job.setSetupEmailFailures(identitySaga.sendSetupEmails(id)); return dto(jobs.save(job)); }

    private Tenant tenant(AbstractAuthenticationToken token) { requireEnabled(); String code = SecurityUtils.getTenantIdFromAuthentication(token); if (code == null || code.isBlank()) throw problem(HttpStatus.UNAUTHORIZED, "Tenant attivo richiesto", "tenant.missing"); Tenants entity = tenants.findByCodeAndDeletedFalse(code).filter(t -> Boolean.TRUE.equals(t.getActive())).orElseThrow(() -> problem(HttpStatus.CONFLICT, "Tenant non attivo", "tenant.inactive")); if (schemas.findActiveTenantCode(entity.getId()).filter(code::equals).isEmpty()) throw problem(HttpStatus.CONFLICT, "Schema tenant non attivo", "tenant.schemaInactive"); return new Tenant(code, entity); }
    private String actor(AbstractAuthenticationToken token) { String actor = SecurityUtils.getUserIdFromAuthentication(token); if (actor == null || actor.isBlank()) throw problem(HttpStatus.UNAUTHORIZED, "Identità richiesta", "identity.missing"); return actor; }
    private void requireEnabled() { if (!properties.isEnabled()) throw problem(HttpStatus.NOT_FOUND, "Onboarding non disponibile", "disabled"); }
    private OnboardingImportJob required(Long id) { return jobs.findByIdAndDeletedFalse(id).orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "Importazione non trovata", "id.notFound")); }
    private long activeUsers() { return users.findAll().stream().filter(u -> !Boolean.TRUE.equals(u.getDeleted()) && Boolean.TRUE.equals(u.getActive())).count(); }
    private long activeInstruments() { return instruments.findAll().stream().filter(i -> !Boolean.TRUE.equals(i.getDeleted())).count(); }
    private Pageable normalize(Pageable p) { return PageRequest.of(Math.max(0, p.getPageNumber()), Math.min(100, Math.max(1, p.getPageSize())), p.getSort().isSorted() ? p.getSort() : Sort.by(Sort.Direction.DESC, "insertDate")); }
    private OnboardingDtos.Job dto(OnboardingImportJob j) { return new OnboardingDtos.Job(j.getId(), j.getFileName(), j.getFormat(), j.getCsvSection(), j.getTemplateVersion(), j.getStatus(), j.getStage(), j.getProgressPercentage(), new OnboardingDtos.Counts(j.getTotalRows(), j.getValidRows(), j.getWarningRows(), j.getErrorRows()), j.getInsertDate(), j.getCompletedAt(), j.getSetupEmailFailures(), j.getLastErrorCode()); }
    private OnboardingDtos.Section dto(OnboardingImportSection s) { return new OnboardingDtos.Section(s.getSection(), s.getTotal(), s.getValid(), s.getWarning(), s.getError(), s.getCreateCount(), s.getReuseCount(), s.getSkipCount(), s.getApplied()); }
    private OnboardingDtos.Row dto(OnboardingImportRow r) { return new OnboardingDtos.Row(r.getId(), r.getSection(), r.getRowNumber(), r.getStatus(), r.getAction(), r.getNormalizedPayload()); }
    private OnboardingDtos.Issue dto(OnboardingImportIssue i) { return new OnboardingDtos.Issue(i.getId(), i.getSeverity(), i.getCode(), i.getSection(), i.getRowNumber(), i.getColumnName(), i.getMessage(), i.getSuggestion()); }
    private RequestAlertException problem(HttpStatus status, String message, String key) { return new RequestAlertException(status, message, ENTITY, key); }
    private record Tenant(String code, Tenants entity) {}
}
