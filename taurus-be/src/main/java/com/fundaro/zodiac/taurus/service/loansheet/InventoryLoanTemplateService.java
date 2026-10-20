package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.web.rest.errors.ErrorArgs;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSettings;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplate;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplateOrigin;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSettingsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanTemplateRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.PdfContent;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.*;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos;
import com.fundaro.zodiac.taurus.service.signature.SignatureRequirementsService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fundaro.zodiac.taurus.service.loansheet.LoanSheetSupport.*;

/**
 * Template of the loan sheet and duration rules of the tenant: verification, preview and activation of the uploaded
 * templates, history, return to the Taurus default and immutable copies of the default used by the issued sheets.
 */
@Service
@Transactional
public class InventoryLoanTemplateService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryLoanTemplateService.class);
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final InventoryLoanTemplateRepository templateRepository;
    private final InventoryLoanSettingsRepository settingsRepository;
    private final LoanTemplateDefaults defaults;
    private final LoanSheetPdfRenderer renderer;
    private final LoanSheetTenantData tenantData;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final SignatureRequirementsService signatureRequirements;

    public InventoryLoanTemplateService(
        InventoryLoanTemplateRepository templateRepository,
        InventoryLoanSettingsRepository settingsRepository,
        LoanTemplateDefaults defaults,
        LoanSheetPdfRenderer renderer,
        LoanSheetTenantData tenantData,
        TenantTimeZoneService tenantTimeZoneService,
        SignatureRequirementsService signatureRequirements
    ) {
        this.templateRepository = templateRepository;
        this.settingsRepository = settingsRepository;
        this.defaults = defaults;
        this.renderer = renderer;
        this.tenantData = tenantData;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.signatureRequirements = signatureRequirements;
    }

    public Overview overview(AbstractAuthenticationToken token) {
        tenant(token);
        return toOverview(settings(actor(token)));
    }

    public Overview updateDurations(DurationsUpdate request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanSettings settings = settings(actor);
        if (settings.getEntityVersion() != request.entityVersion()) {
            throw error(HttpStatus.CONFLICT, "Le regole di durata sono state modificate da un'altra richiesta: ricaricare", "inventory.loanSettings.versionConflict");
        }
        Durations durations = request.durations();
        List<String> problems = new ArrayList<>();
        if (durations.minMonths() > durations.maxMonths()) problems.add("minAboveMax");
        if (durations.maxMonths() > durations.totalMaxMonths()) problems.add("maxAboveTotal");
        if (durations.renewalMaxMonths() > durations.totalMaxMonths()) problems.add("renewalAboveTotal");
        if (!problems.isEmpty()) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Inconsistent loan durations: " + String.join(", ", problems), "inventory.loanSettings.invalid")
                .with("problems", problems);
        }
        settings.setMinMonths(durations.minMonths());
        settings.setMaxMonths(durations.maxMonths());
        settings.setRenewalMaxMonths(durations.renewalMaxMonths());
        settings.setTotalMaxMonths(durations.totalMaxMonths());
        settings.touchAudit(actor);
        settingsRepository.saveAndFlush(settings);
        LOG.info("inventory_loan_template_durations_updated min={} max={} renewal={} total={}",
            durations.minMonths(), durations.maxMonths(), durations.renewalMaxMonths(), durations.totalMaxMonths());
        return toOverview(settings);
    }

    /**
     * Enables or disables the electronic signature of the tenant. Enabling requires the SMTP server, the secret of the
     * codes and the seal of the installation; disabling applies to new sheets and acknowledgments only.
     */
    public Overview updateSignature(SignatureDtos.SettingsUpdate request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanSettings settings = settings(actor);
        if (settings.getEntityVersion() != request.entityVersion()) {
            throw error(HttpStatus.CONFLICT, "Le impostazioni della firma sono state modificate da un'altra richiesta: ricaricare", "signature.settings.versionConflict");
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        if (enabled && !settings.isElectronicSignatureEnabled()) {
            SignatureDtos.Requirements requirements = signatureRequirements.requirements();
            if (!requirements.satisfied()) {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Electronic signature requirements are missing", "signature.settings.requirementsMissing")
                    .with("missing", requirements.missing());
            }
        }
        settings.setElectronicSignatureEnabled(enabled);
        settings.touchAudit(actor);
        settingsRepository.saveAndFlush(settings);
        LOG.info("inventory_electronic_signature_updated enabled={}", enabled);
        return toOverview(settings);
    }

    public CheckResult check(TemplateFile file, AbstractAuthenticationToken token) {
        tenant(token);
        List<Issue> issues = verify(file, token).issues();
        return new CheckResult(!LoanTemplateValidator.hasBlockingIssues(issues), issues);
    }

    public PdfContent preview(TemplateFile file, AbstractAuthenticationToken token) {
        tenant(token);
        Verification verification = verify(file, token);
        requireValid(verification.issues(), "Template preview refused");
        return new PdfContent("anteprima-modello-foglio-affidamento.pdf", verification.preview());
    }

    public Overview activate(TemplateFile file, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        Verification verification = verify(file, token);
        requireValid(verification.issues(), "Template activation refused");
        templateRepository.lockTenantTemplates();
        String hash = sha256(file.content());
        InventoryLoanTemplate template = templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(InventoryLoanTemplateOrigin.CUSTOM, hash)
            .orElseGet(() -> {
                InventoryLoanTemplate created = new InventoryLoanTemplate();
                created.initializeAudit(actor);
                created.setVersionNumber(templateRepository.findMaxVersionNumber() + 1);
                created.setOrigin(InventoryLoanTemplateOrigin.CUSTOM);
                created.setFileName(fileName(file.fileName()));
                created.setContent(file.content());
                created.setContentHash(hash);
                return templateRepository.saveAndFlush(created);
            });
        InventoryLoanSettings settings = settings(actor);
        settings.setActiveTemplate(template);
        settings.touchAudit(actor);
        settingsRepository.saveAndFlush(settings);
        LOG.info("inventory_loan_template_activated templateId={} versionNumber={} hash={}", template.getId(), template.getVersionNumber(), hash);
        return toOverview(settings);
    }

    public Overview activateVersion(long id, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanTemplate template = requiredVersion(id);
        if (template.getOrigin() != InventoryLoanTemplateOrigin.CUSTOM) throw templateNotFound();
        InventoryLoanSettings settings = settings(actor);
        settings.setActiveTemplate(template);
        settings.touchAudit(actor);
        settingsRepository.saveAndFlush(settings);
        LOG.info("inventory_loan_template_activated templateId={} versionNumber={} hash={}", template.getId(), template.getVersionNumber(), template.getContentHash());
        return toOverview(settings);
    }

    public Overview useDefault(AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanSettings settings = settings(actor);
        settings.setActiveTemplate(null);
        settings.touchAudit(actor);
        settingsRepository.saveAndFlush(settings);
        LOG.info("inventory_loan_template_default_in_use label={}", defaults.label());
        return toOverview(settings);
    }

    @Transactional(readOnly = true)
    public TemplateDownload defaultFile(AbstractAuthenticationToken token) {
        tenant(token);
        return new TemplateDownload(LoanTemplateDefaults.FILE_NAME, defaults.content());
    }

    public PdfContent defaultPreview(AbstractAuthenticationToken token) {
        tenant(token);
        return new PdfContent("anteprima-modello-predefinito-foglio-affidamento.pdf", samplePdf(defaults.content(), settings(actor(token)), token));
    }

    @Transactional(readOnly = true)
    public TemplateDownload versionFile(long id, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryLoanTemplate template = requiredVersion(id);
        return new TemplateDownload(template.getOrigin() == InventoryLoanTemplateOrigin.DEFAULT ? LoanTemplateDefaults.FILE_NAME : template.getFileName(), template.getContent());
    }

    public PdfContent versionPreview(long id, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryLoanTemplate template = requiredVersion(id);
        String suffix = template.getVersionNumber() == null ? "predefinito" : "v" + template.getVersionNumber();
        return new PdfContent("anteprima-modello-foglio-affidamento-" + suffix + ".pdf", samplePdf(template.getContent(), settings(actor(token)), token));
    }

    /**
     * Returns the settings of the tenant, creating them with the default duration rules on first use.
     */
    public InventoryLoanSettings settings(String actor) {
        return settingsRepository.findFirstByDeletedFalse().orElseGet(() -> {
            templateRepository.lockTenantTemplates();
            return settingsRepository.findFirstByDeletedFalse().orElseGet(() -> {
                Durations initial = defaults.durations();
                InventoryLoanSettings created = new InventoryLoanSettings();
                created.initializeAudit(actor);
                created.setMinMonths(initial.minMonths());
                created.setMaxMonths(initial.maxMonths());
                created.setRenewalMaxMonths(initial.renewalMaxMonths());
                created.setTotalMaxMonths(initial.totalMaxMonths());
                return settingsRepository.saveAndFlush(created);
            });
        });
    }

    /**
     * Content of the template new sheets are printed with: the uploaded template in use, or the Taurus default.
     */
    public String currentContent(InventoryLoanSettings settings) {
        return settings.getActiveTemplate() == null ? defaults.content() : settings.getActiveTemplate().getContent();
    }

    /**
     * Label of the template new sheets are printed with.
     */
    public String currentLabel(InventoryLoanSettings settings) {
        return settings.getActiveTemplate() == null ? "predefinito " + defaults.label() : label(settings.getActiveTemplate());
    }

    /**
     * Returns the immutable template a new sheet refers to: the uploaded template in use or a copy of the current
     * default, reused when its hash is unchanged.
     */
    public InventoryLoanTemplate templateForIssue(InventoryLoanSettings settings, String actor) {
        if (settings.getActiveTemplate() != null) return settings.getActiveTemplate();
        return templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(InventoryLoanTemplateOrigin.DEFAULT, defaults.contentHash()).orElseGet(() -> {
            templateRepository.lockTenantTemplates();
            return templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(InventoryLoanTemplateOrigin.DEFAULT, defaults.contentHash()).orElseGet(() -> {
                InventoryLoanTemplate copy = new InventoryLoanTemplate();
                copy.initializeAudit(actor);
                copy.setOrigin(InventoryLoanTemplateOrigin.DEFAULT);
                copy.setDefaultLabel(defaults.label());
                copy.setFileName(LoanTemplateDefaults.FILE_NAME);
                copy.setContent(defaults.content());
                copy.setContentHash(defaults.contentHash());
                InventoryLoanTemplate saved = templateRepository.saveAndFlush(copy);
                LOG.info("inventory_loan_template_default_copied templateId={} label={}", saved.getId(), defaults.label());
                return saved;
            });
        });
    }

    public static Durations durations(InventoryLoanSettings settings) {
        return new Durations(settings.getMinMonths(), settings.getMaxMonths(), settings.getRenewalMaxMonths(), settings.getTotalMaxMonths());
    }

    public static Durations durations(InventoryLoanSheet sheet) {
        return new Durations(sheet.getMinMonths(), sheet.getMaxMonths(), sheet.getRenewalMaxMonths(), sheet.getTotalMaxMonths());
    }

    public static String label(InventoryLoanTemplate template) {
        return template.getOrigin() == InventoryLoanTemplateOrigin.DEFAULT
            ? "predefinito " + template.getDefaultLabel()
            : "versione " + template.getVersionNumber();
    }

    private record Verification(List<Issue> issues, byte[] preview) {
    }

    private Verification verify(TemplateFile file, AbstractAuthenticationToken token) {
        if (file.content().getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Il modello supera 2 MB", "inventory.loanTemplate.tooLarge").with("maxMegabytes", (int) (MAX_FILE_BYTES / (1024 * 1024)));
        }
        List<Issue> issues = new ArrayList<>(LoanTemplateValidator.validate(file.content(), renderer));
        // The markers of the electronic signature matter only to the tenants that use it, and never block the template.
        if (settings(actor(token)).isElectronicSignatureEnabled()) issues.addAll(LoanSheetActs.issues(file.content()));
        if (LoanTemplateValidator.hasBlockingIssues(issues)) {
            LOG.info("inventory_loan_template_rejected blockingIssues={}", issues.stream().filter(Issue::blocking).count());
            return new Verification(issues, null);
        }
        byte[] preview = null;
        try {
            preview = samplePdf(file.content(), settings(actor(token)), token);
            if (preview.length > LoanSheetPdfRenderer.MAX_PDF_BYTES) {
                issues.add(LoanTemplateValidator.blocking(
                    "tooLargePdf",
                    ErrorArgs.of("maxMegabytes", (int) (LoanSheetPdfRenderer.MAX_PDF_BYTES / (1024 * 1024))),
                    null
                ));
            }
        } catch (RuntimeException exception) {
            LOG.info("inventory_loan_template_render_failed error={} message={}", exception.getClass().getSimpleName(), exception.getMessage());
            issues.add(LoanTemplateValidator.blocking("renderFailed", null));
        }
        return new Verification(issues, preview);
    }

    private byte[] samplePdf(String template, InventoryLoanSettings settings, AbstractAuthenticationToken token) {
        LoanSheetContent content = LoanSheetSamples.preview(
            tenantData.forTemplate(template, token),
            durations(settings),
            LocalDate.now(tenantTimeZoneService.currentZoneId())
        );
        return renderer.render(template, LoanSheetModelFactory.build(content), true);
    }

    private static void requireValid(List<Issue> issues, String prefix) {
        if (!LoanTemplateValidator.hasBlockingIssues(issues)) return;
        List<ErrorArgs> blocking = issues.stream().filter(Issue::blocking).limit(5)
            .map(issue -> new ErrorArgs().with("code", issue.code()).with("line", issue.line()).with("args", issue.args()))
            .toList();
        String codes = blocking.stream().map(issue -> String.valueOf(issue.values().get("code"))).collect(Collectors.joining(", "));
        throw error(HttpStatus.UNPROCESSABLE_ENTITY, prefix + ": " + codes, "inventory.loanTemplate.invalid").with("issues", blocking);
    }

    private InventoryLoanTemplate requiredVersion(long id) {
        return templateRepository.findByIdAndDeletedFalse(id).orElseThrow(InventoryLoanTemplateService::templateNotFound);
    }

    private static RequestAlertException templateNotFound() {
        return error(HttpStatus.NOT_FOUND, "Versione del modello non trovata", "inventory.loanTemplate.notFound");
    }

    private Overview toOverview(InventoryLoanSettings settings) {
        InventoryLoanTemplate active = settings.getActiveTemplate();
        List<VersionSummary> versions = templateRepository.findAllByOriginAndDeletedFalseOrderByVersionNumberDesc(InventoryLoanTemplateOrigin.CUSTOM).stream()
            .map(template -> toSummary(template, active != null && active.getId().equals(template.getId())))
            .toList();
        return new Overview(
            active == null ? null : toSummary(active, true),
            defaults.label(),
            durations(settings),
            settings.getEntityVersion(),
            versions,
            LoanSheetVariables.GROUPS,
            new Limits(MAX_FILE_BYTES),
            new SignatureDtos.Settings(settings.isElectronicSignatureEnabled(), signatureRequirements.requirements(), LoanSheetActs.issues(currentContent(settings)))
        );
    }

    private static VersionSummary toSummary(InventoryLoanTemplate template, boolean active) {
        return new VersionSummary(
            template.getId(),
            template.getVersionNumber(),
            template.getOrigin(),
            template.getDefaultLabel(),
            template.getFileName(),
            template.getContentHash(),
            template.getInsertDate(),
            active
        );
    }

    /**
     * Keeps only the name of the uploaded file, without folders or control characters.
     */
    static String fileName(String value) {
        String name = value == null ? "" : value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) return "modello-foglio-affidamento.html";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
