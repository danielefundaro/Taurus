package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.*;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.*;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.finance.*;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingDomainApplicationService {
    private final InstrumentsRepository instruments;
    private final UsersRepository users;
    private final UserIdentityRepository identities;
    private final TenantUserMembershipRepository memberships;
    private final TenantsRepository tenants;
    private final InventoryItemRepository inventory;
    private final FinancialCategoryRepository categories;
    private final FinancialAccountRepository accounts;
    private final AccountingYearRepository years;
    private final FinancialMovementRepository movements;
    private final OnboardingImportSectionRepository summaries;
    private final OnboardingImportRowRepository stagingRows;

    public OnboardingDomainApplicationService(InstrumentsRepository instruments, UsersRepository users, UserIdentityRepository identities,
        TenantUserMembershipRepository memberships, TenantsRepository tenants, InventoryItemRepository inventory,
        FinancialCategoryRepository categories, FinancialAccountRepository accounts, AccountingYearRepository years,
        FinancialMovementRepository movements, OnboardingImportSectionRepository summaries, OnboardingImportRowRepository stagingRows) {
        this.instruments = instruments; this.users = users; this.identities = identities; this.memberships = memberships; this.tenants = tenants;
        this.inventory = inventory; this.categories = categories; this.accounts = accounts; this.years = years; this.movements = movements; this.summaries = summaries; this.stagingRows = stagingRows;
    }

    @Transactional
    public void apply(List<OnboardingImportRow> rows, Map<Long, String> keycloakIds, String tenantCode, String actor) {
        Date now = new Date();
        Map<String, Instruments> instrumentRefs = new HashMap<>();
        Map<String, Instruments> instrumentNames = instruments.findAll().stream().filter(i -> !Boolean.TRUE.equals(i.getDeleted())).collect(Collectors.toMap(i -> key(i.getName()), i -> i, (a,b) -> a));
        for (OnboardingImportRow row : scoped(rows, OnboardingSection.INSTRUMENTS)) {
            Instruments entity = instrumentNames.get(key(value(row, "nome")));
            if (entity == null) { entity = new Instruments(); audit(entity, actor, now); entity.setName(value(row, "nome")); entity.setDescription(blankToNull(value(row, "descrizione"))); entity = instruments.save(entity); instrumentNames.put(key(entity.getName()), entity); }
            instrumentRefs.put(value(row, "riferimento").toUpperCase(Locale.ROOT), entity); applied(row);
        }
        for (Map.Entry<String, Instruments> entry : instrumentNames.entrySet()) instrumentRefs.putIfAbsent(entry.getKey().toUpperCase(Locale.ROOT), entry.getValue());

        Tenants tenant = tenants.findByCodeAndDeletedFalse(tenantCode).orElseThrow();
        for (OnboardingImportRow row : scoped(rows, OnboardingSection.USERS)) {
            if (row.getAction() == OnboardingRowAction.SKIP) { skipped(row); continue; }
            String keycloakId = Objects.requireNonNull(keycloakIds.get(row.getId()), "Prepared identity missing");
            UserIdentity identity = identities.findByKeycloakId(keycloakId).orElseGet(() -> { UserIdentity created = new UserIdentity(); created.setKeycloakId(keycloakId); created.initializeAudit(actor); return identities.save(created); });
            Users entity = new Users(); audit(entity, actor, now); entity.setName(value(row, "nome")); entity.setLastName(value(row, "cognome")); entity.setEmail(value(row, "email")); entity.setKeycloakId(keycloakId); entity.setUserIdentity(identity);
            entity.setActive("SI".equals(value(row, "attivo"))); if (!value(row, "data_nascita").isBlank()) entity.setBirthDate(java.sql.Date.valueOf(value(row, "data_nascita")));
            entity.setRoles(split(value(row, "ruoli")).stream().map(role -> RoleEnum.valueOf("ROLE_" + role)).collect(Collectors.toCollection(LinkedHashSet::new)));
            entity.setInstruments(split(value(row, "strumenti")).stream().map(ref -> instrumentRefs.get(ref.toUpperCase(Locale.ROOT))).filter(Objects::nonNull).toList()); users.save(entity);
            TenantUserMembership membership = memberships.findById(new TenantUserMembershipId(tenant.getId(), identity.getId())).orElseGet(TenantUserMembership::new);
            membership.setId(new TenantUserMembershipId(tenant.getId(), identity.getId())); membership.setActive(true); membership.setJoinedAt(ZonedDateTime.now()); membership.setLeftAt(null); membership.initializeAudit(actor); memberships.save(membership); applied(row);
        }

        for (OnboardingImportRow row : scoped(rows, OnboardingSection.INVENTORY)) {
            InventoryItem item = new InventoryItem(); item.initializeAudit(actor); item.setInventoryNumber(value(row, "numero_inventario")); item.setName(value(row, "nome")); item.setDescription(blankToNull(value(row, "descrizione")));
            item.setTotalQuantity(integer(row, "quantita_totale")); item.setEstimatedUnitValue(decimalOrNull(row, "valore_unitario_stimato")); item.setCurrency(blankToNull(value(row, "valuta"))); item.setConditionStatus(InventoryCondition.valueOf(value(row, "condizione"))); item.setConditionNotes(blankToNull(value(row, "note_condizione"))); inventory.save(item); applied(row);
        }

        Map<String, FinancialCategory> categoryNames = categories.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().stream().collect(Collectors.toMap(c -> key(c.getName()), c -> c, (a,b) -> a));
        int categoryOrder = categoryNames.values().stream().mapToInt(FinancialCategory::getDisplayOrder).max().orElse(0);
        for (OnboardingImportRow row : scoped(rows, OnboardingSection.CATEGORIES)) {
            FinancialCategory category = categoryNames.get(key(value(row, "nome")));
            if (category == null) { category = new FinancialCategory(); category.initializeAudit(actor); category.setName(value(row, "nome")); category.setDescription(blankToNull(value(row, "descrizione"))); category.setDirection(FinancialCategoryDirection.valueOf(value(row, "direzione"))); category.setActive(true); category.setSystemDefined(false); category.setDisplayOrder(value(row, "ordine").isBlank() ? ++categoryOrder : integer(row, "ordine")); category = categories.save(category); categoryNames.put(key(category.getName()), category); }
            applied(row);
        }

        Map<String, FinancialAccount> accountRefs = new HashMap<>(); Map<String, FinancialAccount> accountNames = accounts.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().stream().collect(Collectors.toMap(a -> key(a.getName()), a -> a, (x,y) -> x));
        int accountOrder = accountNames.values().stream().mapToInt(FinancialAccount::getDisplayOrder).max().orElse(0);
        for (OnboardingImportRow row : scoped(rows, OnboardingSection.ACCOUNTS)) {
            FinancialAccount account = accountNames.get(key(value(row, "nome")));
            if (account == null) { account = new FinancialAccount(); account.initializeAudit(actor); account.setName(value(row, "nome")); account.setDescription(blankToNull(value(row, "descrizione"))); account.setAccountType(FinancialAccountType.valueOf(value(row, "tipo"))); account.setCurrency(value(row, "valuta")); account.setIban(blankToNull(value(row, "iban"))); account.setBankName(blankToNull(value(row, "banca"))); account.setActive(true); account.setDisplayOrder(value(row, "ordine").isBlank() ? ++accountOrder : integer(row, "ordine")); account = accounts.save(account); accountNames.put(key(account.getName()), account); }
            accountRefs.put(value(row, "riferimento").toUpperCase(Locale.ROOT), account); applied(row);
        }
        accountNames.forEach((name, account) -> accountRefs.putIfAbsent(name.toUpperCase(Locale.ROOT), account));

        for (OnboardingImportRow row : scoped(rows, OnboardingSection.OPENING_BALANCES)) {
            BigDecimal signed = decimal(row, "importo"); if (signed.signum() == 0) { skipped(row); continue; }
            String reference = value(row, "conto"); FinancialAccount account = accountRefs.get(reference.toUpperCase(Locale.ROOT)); if (account == null) account = accountNames.get(key(reference));
            if (account == null || movements.countByAccount_IdAndDeletedFalse(account.getId()) > 0) throw new IllegalStateException("OPENING_BALANCE_NOT_ALLOWED");
            LocalDate date = LocalDate.parse(value(row, "data")); AccountingYear year = years.findByYearAndDeletedFalse(date.getYear()).orElseGet(() -> { AccountingYear created = new AccountingYear(); created.initializeAudit(actor); created.setYear(date.getYear()); created.setStartDate(LocalDate.of(date.getYear(), 1, 1)); created.setEndDate(LocalDate.of(date.getYear(), 12, 31)); created.setStatus(AccountingYearStatus.OPEN); return years.save(created); });
            FinancialMovement movement = new FinancialMovement(); movement.initializeAudit(actor); movement.setAccountingYear(year); movement.setAccount(account); movement.setDirection(signed.signum() > 0 ? FinancialDirection.INCOME : FinancialDirection.EXPENSE); movement.setNature(FinancialMovementNature.OPENING); movement.setBookingDate(date); movement.setValueDate(date); movement.setAmount(signed.abs()); movement.setCurrency(account.getCurrency()); movement.setDescription("Saldo iniziale"); movements.save(movement); applied(row);
        }
        Map<OnboardingSection, Long> applied = rows.stream().filter(r -> r.getStatus() == OnboardingRowStatus.APPLIED).collect(Collectors.groupingBy(OnboardingImportRow::getSection, Collectors.counting()));
        for (OnboardingImportSection summary : summaries.findAllByJob_IdOrderBySectionAsc(rows.get(0).getJob().getId())) { summary.setApplied(Math.toIntExact(applied.getOrDefault(summary.getSection(), 0L))); summaries.save(summary); }
        stagingRows.saveAll(rows);
    }

    private static List<OnboardingImportRow> scoped(List<OnboardingImportRow> rows, OnboardingSection section) {
        return rows.stream()
            .filter(row -> row.getSection() == section)
            .filter(row -> row.getStatus() == OnboardingRowStatus.VALID || row.getStatus() == OnboardingRowStatus.WARNING)
            .toList();
    }
    private static void applied(OnboardingImportRow row) { row.setStatus(OnboardingRowStatus.APPLIED); }
    private static void skipped(OnboardingImportRow row) { row.setStatus(OnboardingRowStatus.SKIPPED); }
    private static String value(OnboardingImportRow row, String key) { return Objects.toString(row.getNormalizedPayload().get(key), ""); }
    private static int integer(OnboardingImportRow row, String key) { return Integer.parseInt(value(row, key)); }
    private static BigDecimal decimal(OnboardingImportRow row, String key) { return new BigDecimal(value(row, key)); }
    private static BigDecimal decimalOrNull(OnboardingImportRow row, String key) { return value(row, key).isBlank() ? null : decimal(row, key); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isBlank()).toList(); }
    private static String key(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static void audit(CommonFieldsOpenSearch entity, String actor, Date now) { entity.setDeleted(false); entity.setInsertBy(actor); entity.setEditBy(actor); entity.setInsertDate(now); entity.setEditDate(now); entity.setEntityVersion(0L); }
}
