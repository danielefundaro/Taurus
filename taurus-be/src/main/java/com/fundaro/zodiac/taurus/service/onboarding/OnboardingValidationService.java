package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.*;
import com.fundaro.zodiac.taurus.domain.finance.*;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.finance.*;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingValidationService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> ROLES = Set.of("ADMIN", "TREASURER", "ARCHIVIST", "USER", "USER_EXTERNAL");
    private final OnboardingFileInspectionService inspection;
    private final OnboardingImportJobRepository jobs;
    private final OnboardingImportRowRepository rows;
    private final OnboardingImportIssueRepository issues;
    private final OnboardingImportSectionRepository sections;
    private final InstrumentsRepository instruments;
    private final UsersRepository users;
    private final InventoryItemRepository inventory;
    private final FinancialCategoryRepository categories;
    private final FinancialAccountRepository accounts;
    private final TenantsRepository tenants;

    public OnboardingValidationService(OnboardingFileInspectionService inspection, OnboardingImportJobRepository jobs,
        OnboardingImportRowRepository rows, OnboardingImportIssueRepository issues, OnboardingImportSectionRepository sections,
        InstrumentsRepository instruments, UsersRepository users, InventoryItemRepository inventory,
        FinancialCategoryRepository categories, FinancialAccountRepository accounts, TenantsRepository tenants) {
        this.inspection = inspection; this.jobs = jobs; this.rows = rows; this.issues = issues; this.sections = sections;
        this.instruments = instruments; this.users = users; this.inventory = inventory; this.categories = categories;
        this.accounts = accounts; this.tenants = tenants;
    }

    @Transactional
    public void validate(Long jobId, byte[] content) {
        OnboardingImportJob job = jobs.findForUpdate(jobId).orElseThrow();
        if (job.getStatus() != OnboardingJobStatus.UPLOADED && job.getStatus() != OnboardingJobStatus.INVALID && job.getStatus() != OnboardingJobStatus.FAILED) return;
        job.setStatus(OnboardingJobStatus.VALIDATING); job.setStage("INSPECTING_FILE"); job.setProgressPercentage(5); job.setStartedAt(ZonedDateTime.now()); job.setLastErrorCode(null);
        issues.deleteAllByJob_Id(jobId); rows.deleteAllByJob_Id(jobId); sections.deleteAllByJob_Id(jobId);
        try {
            Set<OnboardingSection> selected = parseSelected(job.getSelectedSections());
            OnboardingFileInspectionService.Inspection parsed = inspection.inspect(content, job.getFormat(), job.getCsvSection(), selected);
            if (parsed.rows().isEmpty()) throw new OnboardingFileInspectionService.InspectionException("FILE_NO_DATA", "Il file non contiene righe dati");
            job.setStage("VALIDATING_ROWS"); job.setProgressPercentage(20);
            ValidationState state = new ValidationState(job, existingInstruments(), existingUsers(), existingCategories(), existingAccounts());
            for (String warning : parsed.warnings()) state.global(OnboardingIssueSeverity.WARNING, "SHEET_UNKNOWN", warning);
            for (OnboardingFileInspectionService.ParsedRow source : parsed.rows()) validateRow(state, source);
            validateReferences(state);
            validateUserLimit(state);
            persist(state);
            job.setTotalRows(state.persistedRows.size());
            job.setValidRows((int) state.persistedRows.stream().filter(r -> r.getStatus() == OnboardingRowStatus.VALID).count());
            job.setWarningRows((int) state.persistedRows.stream().filter(r -> r.getStatus() == OnboardingRowStatus.WARNING).count());
            job.setErrorRows((int) state.persistedRows.stream().filter(r -> r.getStatus() == OnboardingRowStatus.ERROR).count());
            job.setStatus(job.getErrorRows() == 0 ? OnboardingJobStatus.READY : OnboardingJobStatus.INVALID);
            job.setStage(job.getErrorRows() == 0 ? "VALIDATION_COMPLETED" : "VALIDATION_FAILED"); job.setProgressPercentage(100); job.setCompletedAt(ZonedDateTime.now());
        } catch (OnboardingFileInspectionService.InspectionException exception) {
            OnboardingImportIssue issue = new OnboardingImportIssue(); issue.setJob(job); issue.setSeverity(OnboardingIssueSeverity.ERROR);
            issue.setCode(exception.getCode()); issue.setMessage(exception.getMessage()); issues.save(issue);
            job.setStatus(OnboardingJobStatus.INVALID); job.setStage("FILE_REJECTED"); job.setProgressPercentage(100); job.setErrorRows(1); job.setLastErrorCode(exception.getCode()); job.setCompletedAt(ZonedDateTime.now());
        } catch (RuntimeException exception) {
            job.setStatus(OnboardingJobStatus.FAILED); job.setStage("VALIDATION_FAILED"); job.setLastErrorCode("VALIDATION_TECHNICAL_FAILURE"); job.setCompletedAt(ZonedDateTime.now());
        }
        jobs.save(job);
    }

    private void validateRow(ValidationState state, OnboardingFileInspectionService.ParsedRow source) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        source.values().forEach((key, value) -> payload.put(key, value == null ? "" : value.trim()));
        normalize(source.section(), payload);
        OnboardingImportRow row = new OnboardingImportRow(); row.setJob(state.job); row.setSection(source.section()); row.setRowNumber(source.rowNumber()); row.setNormalizedPayload(payload);
        List<IssueDraft> drafts = state.drafts.computeIfAbsent(row, ignored -> new ArrayList<>());
        validateRequired(source.section(), payload, drafts);
        validateTypes(source.section(), payload, drafts);
        String localKey = key(source.section(), payload);
        if (localKey != null && !state.keys.computeIfAbsent(source.section(), ignored -> new HashSet<>()).add(localKey)) error(drafts, "DUPLICATE_IN_FILE", keyColumn(source.section()), "Valore duplicato nel file");
        reconcile(state, row, drafts);
        row.setStatus(drafts.stream().anyMatch(i -> i.severity == OnboardingIssueSeverity.ERROR) ? OnboardingRowStatus.ERROR : drafts.isEmpty() ? OnboardingRowStatus.VALID : OnboardingRowStatus.WARNING);
        state.persistedRows.add(row);
    }

    private void validateRequired(OnboardingSection section, Map<String, Object> p, List<IssueDraft> d) {
        List<String> required = switch (section) {
            case INSTRUMENTS -> List.of("riferimento", "nome");
            case USERS -> List.of("riferimento", "nome", "cognome", "email", "ruoli");
            case INVENTORY -> List.of("numero_inventario", "nome", "quantita_totale", "condizione");
            case CATEGORIES -> List.of("nome", "direzione");
            case ACCOUNTS -> List.of("riferimento", "nome", "tipo", "valuta");
            case OPENING_BALANCES -> List.of("conto", "data", "importo");
        };
        for (String field : required) if (text(p, field).isBlank()) error(d, "VALUE_REQUIRED", field, "Valore obbligatorio mancante");
    }

    private void validateTypes(OnboardingSection section, Map<String, Object> p, List<IssueDraft> d) {
        try {
            switch (section) {
                case USERS -> {
                    if (!text(p, "email").isBlank() && !EMAIL.matcher(text(p, "email")).matches()) error(d, "VALUE_INVALID_EMAIL", "email", "Indirizzo e-mail non valido");
                    for (String role : split(text(p, "ruoli"))) if (!ROLES.contains(role)) error(d, "VALUE_INVALID_ENUM", "ruoli", "Ruolo non ammesso: " + role);
                    if (!text(p, "data_nascita").isBlank()) LocalDate.parse(text(p, "data_nascita"));
                    if (!Set.of("SI", "NO").contains(text(p, "attivo"))) error(d, "VALUE_INVALID_ENUM", "attivo", "Usare SI oppure NO");
                }
                case INVENTORY -> {
                    int quantity = Integer.parseInt(text(p, "quantita_totale")); if (quantity < 0) throw new NumberFormatException(); p.put("quantita_totale", quantity);
                    if (!text(p, "valore_unitario_stimato").isBlank()) { BigDecimal value = decimal(text(p, "valore_unitario_stimato")); if (value.signum() < 0) throw new NumberFormatException(); p.put("valore_unitario_stimato", value); }
                    if (!text(p, "condizione").isBlank()) InventoryCondition.valueOf(text(p, "condizione"));
                    currency(p, "valuta", !text(p, "valore_unitario_stimato").isBlank(), d);
                }
                case CATEGORIES -> { FinancialCategoryDirection.valueOf(text(p, "direzione")); optionalInteger(p, "ordine"); }
                case ACCOUNTS -> {
                    FinancialAccountType type = FinancialAccountType.valueOf(text(p, "tipo")); currency(p, "valuta", true, d); optionalInteger(p, "ordine");
                    if (type == FinancialAccountType.CASH && (!text(p, "iban").isBlank() || !text(p, "banca").isBlank())) error(d, "VALUE_NOT_ALLOWED", "iban", "IBAN e banca sono ammessi soltanto per conti BANK");
                    if (text(p, "iban").length() > 34) error(d, "VALUE_TOO_LONG", "iban", "IBAN oltre 34 caratteri");
                }
                case OPENING_BALANCES -> { LocalDate.parse(text(p, "data")); p.put("importo", decimal(text(p, "importo"))); }
                default -> { }
            }
        } catch (IllegalArgumentException exception) { error(d, "VALUE_INVALID_FORMAT", null, "Uno o più valori hanno formato non valido"); }
        lengths(section, p, d);
    }

    private void reconcile(ValidationState state, OnboardingImportRow row, List<IssueDraft> d) {
        Map<String, Object> p = row.getNormalizedPayload(); String name = normalizeKey(text(p, "nome"));
        switch (row.getSection()) {
            case INSTRUMENTS -> { if (state.instrumentNames.contains(name)) reuse(row, d, "Strumento esistente: verrà riutilizzato"); }
            case USERS -> {
                Users existing = state.usersByEmail.get(text(p, "email"));
                if (existing != null) {
                    boolean same = normalizeKey(existing.getName()).equals(normalizeKey(text(p, "nome"))) && normalizeKey(existing.getLastName()).equals(normalizeKey(text(p, "cognome")));
                    if (same) skip(row, d, "Utente già presente con gli stessi dati"); else error(d, "CONFLICT_EXISTING_RECORD", "email", "Utente già presente con dati differenti");
                }
            }
            case INVENTORY -> { if (inventory.existsByInventoryNumberIgnoreCaseAndDeletedFalse(text(p, "numero_inventario"))) error(d, "CONFLICT_EXISTING_RECORD", "numero_inventario", "Numero inventario già attivo"); }
            case CATEGORIES -> {
                FinancialCategory existing = state.categoriesByName.get(name);
                if (existing != null) { if (existing.getDirection().name().equals(text(p, "direzione"))) reuse(row, d, "Categoria esistente: verrà riutilizzata"); else error(d, "CONFLICT_EXISTING_RECORD", "direzione", "Categoria esistente con direzione incompatibile"); }
            }
            case ACCOUNTS -> {
                FinancialAccount existing = state.accountsByName.get(name);
                if (existing != null) { if (existing.getAccountType().name().equals(text(p, "tipo")) && existing.getCurrency().equals(text(p, "valuta"))) reuse(row, d, "Conto esistente: verrà riutilizzato"); else error(d, "CONFLICT_EXISTING_RECORD", "nome", "Conto esistente con tipo o valuta differenti"); }
            }
            default -> { }
        }
    }

    private void validateReferences(ValidationState state) {
        Set<String> fileInstruments = state.persistedRows.stream().filter(r -> r.getSection() == OnboardingSection.INSTRUMENTS).map(r -> text(r.getNormalizedPayload(), "riferimento").toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> existingNames = state.instrumentNames;
        Set<String> fileAccounts = state.persistedRows.stream().filter(r -> r.getSection() == OnboardingSection.ACCOUNTS).map(r -> text(r.getNormalizedPayload(), "riferimento").toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> existingAccountNames = state.accountsByName.keySet();
        for (OnboardingImportRow row : state.persistedRows) {
            List<IssueDraft> d = state.drafts.get(row);
            if (row.getSection() == OnboardingSection.USERS) for (String ref : split(text(row.getNormalizedPayload(), "strumenti")))
                if (!fileInstruments.contains(ref.toUpperCase(Locale.ROOT)) && !existingNames.contains(normalizeKey(ref))) error(d, "REFERENCE_NOT_FOUND", "strumenti", "Strumento non trovato: " + ref);
            if (row.getSection() == OnboardingSection.OPENING_BALANCES) {
                String ref = text(row.getNormalizedPayload(), "conto"); if (!fileAccounts.contains(ref.toUpperCase(Locale.ROOT)) && !existingAccountNames.contains(normalizeKey(ref))) error(d, "REFERENCE_NOT_FOUND", "conto", "Conto non trovato");
            }
            row.setStatus(d.stream().anyMatch(i -> i.severity == OnboardingIssueSeverity.ERROR) ? OnboardingRowStatus.ERROR : d.isEmpty() ? OnboardingRowStatus.VALID : OnboardingRowStatus.WARNING);
        }
    }

    private void validateUserLimit(ValidationState state) {
        String tenantCode = TenantContext.getTenantCode().orElse(null);
        if (tenantCode == null) return;
        Long limit = tenants.findByCodeAndDeletedFalse(tenantCode).map(Tenants::getMaxUsers).orElse(null);
        if (limit == null || limit < 0) return;
        long current = state.usersByEmail.size();
        List<OnboardingImportRow> additions = state.persistedRows.stream().filter(row -> row.getSection() == OnboardingSection.USERS && row.getAction() == OnboardingRowAction.CREATE && row.getStatus() != OnboardingRowStatus.ERROR).toList();
        if (current + additions.size() <= limit) return;
        for (OnboardingImportRow row : additions) {
            error(state.drafts.get(row), "TENANT_USER_LIMIT_EXCEEDED", "email", "Il pacchetto supera il limite utenti del tenant");
            row.setStatus(OnboardingRowStatus.ERROR);
        }
    }

    private void persist(ValidationState state) {
        rows.saveAll(state.persistedRows);
        for (Map.Entry<OnboardingImportRow, List<IssueDraft>> entry : state.drafts.entrySet()) for (IssueDraft draft : entry.getValue()) {
            OnboardingImportIssue issue = new OnboardingImportIssue(); issue.setJob(state.job); issue.setRow(entry.getKey()); issue.setSection(entry.getKey().getSection()); issue.setRowNumber(entry.getKey().getRowNumber());
            issue.setSeverity(draft.severity); issue.setCode(draft.code); issue.setColumnName(draft.column); issue.setMessage(draft.message); issues.save(issue);
        }
        for (OnboardingSection value : OnboardingSection.values()) {
            List<OnboardingImportRow> scoped = state.persistedRows.stream().filter(r -> r.getSection() == value).toList(); if (scoped.isEmpty()) continue;
            OnboardingImportSection summary = new OnboardingImportSection(); summary.setJob(state.job); summary.setSection(value); summary.setTotal(scoped.size());
            summary.setValid((int) scoped.stream().filter(r -> r.getStatus() == OnboardingRowStatus.VALID).count()); summary.setWarning((int) scoped.stream().filter(r -> r.getStatus() == OnboardingRowStatus.WARNING).count()); summary.setError((int) scoped.stream().filter(r -> r.getStatus() == OnboardingRowStatus.ERROR).count());
            summary.setCreateCount((int) scoped.stream().filter(r -> r.getAction() == OnboardingRowAction.CREATE).count()); summary.setReuseCount((int) scoped.stream().filter(r -> r.getAction() == OnboardingRowAction.REUSE).count()); summary.setSkipCount((int) scoped.stream().filter(r -> r.getAction() == OnboardingRowAction.SKIP).count()); sections.save(summary);
        }
        for (IssueDraft draft : state.globalDrafts) { OnboardingImportIssue issue = new OnboardingImportIssue(); issue.setJob(state.job); issue.setSeverity(draft.severity); issue.setCode(draft.code); issue.setMessage(draft.message); issues.save(issue); }
    }

    private void normalize(OnboardingSection section, Map<String, Object> p) {
        for (Map.Entry<String, Object> e : p.entrySet()) e.setValue(Objects.toString(e.getValue(), "").trim());
        for (String key : List.of("valuta", "condizione", "direzione", "tipo", "attivo")) if (p.containsKey(key)) p.put(key, text(p, key).toUpperCase(Locale.ROOT));
        if (p.containsKey("email")) p.put("email", text(p, "email").toLowerCase(Locale.ROOT));
        if (p.containsKey("iban")) p.put("iban", text(p, "iban").replace(" ", "").toUpperCase(Locale.ROOT));
        if (section == OnboardingSection.USERS) { p.put("ruoli", String.join("|", split(text(p, "ruoli")))); p.put("strumenti", String.join("|", split(text(p, "strumenti")))); if (text(p, "attivo").isBlank()) p.put("attivo", "SI"); }
    }
    private void lengths(OnboardingSection section, Map<String, Object> p, List<IssueDraft> d) {
        Map<String, Integer> limits = Map.of("riferimento", 64, "nome", 255, "numero_inventario", 128, "note_condizione", 2000, "iban", 34, "banca", 255);
        limits.forEach((key, limit) -> { if (p.containsKey(key) && text(p, key).length() > limit) error(d, "VALUE_TOO_LONG", key, "Valore oltre " + limit + " caratteri"); });
    }
    private void currency(Map<String, Object> p, String key, boolean required, List<IssueDraft> d) { String value = text(p, key); if ((required && value.isBlank()) || (!value.isBlank() && !value.matches("[A-Z]{3}"))) error(d, "VALUE_INVALID_CURRENCY", key, "Valuta ISO 4217 non valida"); }
    private void optionalInteger(Map<String, Object> p, String key) { if (!text(p, key).isBlank()) p.put(key, Integer.parseInt(text(p, key))); }
    private BigDecimal decimal(String value) { BigDecimal d = new BigDecimal(value); if (d.scale() > 4) throw new NumberFormatException(); return d; }
    private String key(OnboardingSection s, Map<String, Object> p) { return switch (s) { case INSTRUMENTS, USERS, ACCOUNTS -> text(p, "riferimento").toUpperCase(Locale.ROOT); case INVENTORY -> normalizeKey(text(p, "numero_inventario")); case CATEGORIES -> normalizeKey(text(p, "nome")); case OPENING_BALANCES -> normalizeKey(text(p, "conto")); }; }
    private String keyColumn(OnboardingSection s) { return switch (s) { case INSTRUMENTS, USERS, ACCOUNTS -> "riferimento"; case INVENTORY -> "numero_inventario"; case CATEGORIES -> "nome"; case OPENING_BALANCES -> "conto"; }; }
    private Set<String> existingInstruments() { return instruments.findAll().stream().filter(i -> !Boolean.TRUE.equals(i.getDeleted())).map(i -> normalizeKey(i.getName())).collect(Collectors.toSet()); }
    private Map<String, Users> existingUsers() { return users.findAll().stream().filter(u -> !Boolean.TRUE.equals(u.getDeleted()) && u.getEmail() != null).collect(Collectors.toMap(u -> u.getEmail().trim().toLowerCase(Locale.ROOT), u -> u, (a,b) -> a)); }
    private Map<String, FinancialCategory> existingCategories() { return categories.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().stream().collect(Collectors.toMap(c -> normalizeKey(c.getName()), c -> c, (a,b) -> a)); }
    private Map<String, FinancialAccount> existingAccounts() { return accounts.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().stream().collect(Collectors.toMap(a -> normalizeKey(a.getName()), a -> a, (x,y) -> x)); }
    private Set<OnboardingSection> parseSelected(String value) { if (value == null || value.isBlank()) return EnumSet.allOf(OnboardingSection.class); EnumSet<OnboardingSection> result = EnumSet.noneOf(OnboardingSection.class); for (String item : value.split(",")) result.add(OnboardingSection.valueOf(item)); return result; }
    private static String text(Map<String, Object> p, String key) { return Objects.toString(p.get(key), ""); }
    private static String normalizeKey(String value) { return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT); }
    private static List<String> split(String value) { if (value == null || value.isBlank()) return List.of(); return Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isBlank()).map(v -> v.toUpperCase(Locale.ROOT)).toList(); }
    private static void error(List<IssueDraft> d, String code, String column, String message) { d.add(new IssueDraft(OnboardingIssueSeverity.ERROR, code, column, message)); }
    private static void reuse(OnboardingImportRow row, List<IssueDraft> d, String message) { row.setAction(OnboardingRowAction.REUSE); d.add(new IssueDraft(OnboardingIssueSeverity.WARNING, "EXISTING_RECORD_WILL_BE_REUSED", null, message)); }
    private static void skip(OnboardingImportRow row, List<IssueDraft> d, String message) { row.setAction(OnboardingRowAction.SKIP); d.add(new IssueDraft(OnboardingIssueSeverity.WARNING, "EXISTING_RECORD_WILL_BE_SKIPPED", null, message)); }
    private record IssueDraft(OnboardingIssueSeverity severity, String code, String column, String message) {}
    private static final class ValidationState {
        final OnboardingImportJob job; final Set<String> instrumentNames; final Map<String, Users> usersByEmail; final Map<String, FinancialCategory> categoriesByName; final Map<String, FinancialAccount> accountsByName;
        final List<OnboardingImportRow> persistedRows = new ArrayList<>(); final Map<OnboardingImportRow, List<IssueDraft>> drafts = new LinkedHashMap<>(); final Map<OnboardingSection, Set<String>> keys = new EnumMap<>(OnboardingSection.class); final List<IssueDraft> globalDrafts = new ArrayList<>();
        ValidationState(OnboardingImportJob job, Set<String> i, Map<String, Users> u, Map<String, FinancialCategory> c, Map<String, FinancialAccount> a) { this.job=job; instrumentNames=i; usersByEmail=u; categoriesByName=c; accountsByName=a; }
        void global(OnboardingIssueSeverity s, String c, String m) { globalDrafts.add(new IssueDraft(s,c,null,m)); }
    }
}
