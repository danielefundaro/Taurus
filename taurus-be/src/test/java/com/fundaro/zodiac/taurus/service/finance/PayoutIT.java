package com.fundaro.zodiac.taurus.service.finance;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.AdjustmentRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventPayoutDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementLineRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementRequest;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shares of the participants on PostgreSQL: the constraints of the three tables, the isolation between tenants, the
 * settlement written with its movement and released by the deletion of the movement, the system category and the
 * tenant switch off by default.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class PayoutIT {

    @MockitoBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantsRepository tenantsRegistry;
    @Autowired
    FinanceService financeService;
    @Autowired
    PayoutService payoutService;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @PersistenceContext
    EntityManager entityManager;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final String ownerTenant = "payout-owner-" + run;
    private final String intruderTenant = "payout-intruder-" + run;

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provisionBothTenants() {
        registerTenant(ownerTenant);
        registerTenant(intruderTenant);
        provisioningService.provision(ownerTenant);
        provisioningService.provision(intruderTenant);
    }

    @AfterEach
    void dropBothTenants() {
        try {
            provisioningService.dropSchema(ownerTenant);
            provisioningService.dropSchema(intruderTenant);
        } finally {
            tenantsRegistry.findByCodeAndDeletedFalse(ownerTenant).ifPresent(tenantsRegistry::delete);
            tenantsRegistry.findByCodeAndDeletedFalse(intruderTenant).ifPresent(tenantsRegistry::delete);
            jdbcTemplate.update("DELETE FROM public.user_identity WHERE keycloak_id LIKE ?", prefix() + "%");
        }
    }

    @Test
    void leavesTheSwitchOffAndCreatesTheSystemCategory() {
        Boolean enabled = jdbcTemplate.queryForObject("SELECT event_payouts_enabled FROM public.tenant WHERE code = ?", Boolean.class, ownerTenant);
        assertThat(enabled).isFalse();
        Long categories = id(ownerTenant, "SELECT count(*) FROM financial_category WHERE name = 'Compensi ai presenti'"
            + " AND direction = 'EXPENSE' AND system_defined = TRUE AND active = TRUE AND deleted = FALSE");
        assertThat(categories).isEqualTo(1L);
    }

    @Test
    void settlesWithOneMovementAndReleasesTheSharesWhenTheMovementIsDeleted() {
        List<Long> users = users(ownerTenant, 4);
        Long eventId = event(ownerTenant, "Concerto di primavera", "1000.00", users);
        AccountDTO account = transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO cash = financeService.createAccount(cashAccount(), owner());
            payoutService.createSetting(new SettingRequest(LocalDate.of(2026, 1, 1), new BigDecimal("20"), new BigDecimal("5")), owner());
            financeService.createMovement(income(cash.id(), eventId, "1000.00"), owner());
            payoutService.setAdjustment(eventId, users.get(3), new AdjustmentRequest(null, new BigDecimal("180"), "Solista ingaggiato"), owner());
            return cash;
        });

        // 1000 − 180 fixed − 200 association share = 620, divided among three: 206,66 rounded down to 205.
        EventPayoutDTO before = simulate(eventId);
        assertThat(before.lines()).extracting(EventLineDTO::realShare)
            .usingElementComparator(BigDecimal::compareTo)
            .contains(new BigDecimal("205"), new BigDecimal("180"));
        Long first = users.getFirst();

        SettlementDTO settlement = transactionExecutor.execute(ownerTenant, () -> payoutService.settle(new SettlementRequest(
            account.id(), null, LocalDate.of(2026, 4, 1), null, null, UUID.randomUUID(),
            List.of(new SettlementLineRequest(eventId, first, new BigDecimal("150")))
        ), owner()));

        Long categoryId = id(ownerTenant, "SELECT category_id FROM financial_movement WHERE id = " + settlement.movementId());
        assertThat(categoryId).isEqualTo(id(ownerTenant, "SELECT id FROM financial_category WHERE name = 'Compensi ai presenti'"));
        assertThat(id(ownerTenant, "SELECT count(*) FROM event_payout_settlement WHERE movement_id = " + settlement.movementId()))
            .isEqualTo(1L);
        EventLineDTO paid = line(simulate(eventId), first);
        assertThat(paid.realShare()).isEqualByComparingTo("205");
        assertThat(paid.settled()).isEqualByComparingTo("150");
        assertThat(paid.due()).isEqualByComparingTo("55");
        assertThat(simulate(eventId).actual().expenses()).isEqualByComparingTo("0");

        transactionExecutor.execute(ownerTenant, () -> financeService.deleteMovement(settlement.movementId(), owner()));

        assertThat(id(ownerTenant, "SELECT count(*) FROM event_payout_settlement WHERE deleted = FALSE")).isZero();
        assertThat(line(simulate(eventId), first).due()).isEqualByComparingTo("205");
    }

    @Test
    void writesNothingWhenASettlementIsRefused() {
        List<Long> users = users(ownerTenant, 2);
        Long eventId = event(ownerTenant, "Serata danzante", "100.00", users);
        AccountDTO account = transactionExecutor.execute(ownerTenant, () -> {
            payoutService.createSetting(new SettingRequest(LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BigDecimal.ZERO), owner());
            return financeService.createAccount(cashAccount(), owner());
        });
        Long movementsBefore = id(ownerTenant, "SELECT count(*) FROM financial_movement");

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () -> payoutService.settle(new SettlementRequest(
            account.id(), null, LocalDate.of(2026, 4, 1), null, null, null,
            List.of(new SettlementLineRequest(eventId, users.getFirst(), new BigDecimal("10")))
        ), owner())))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("payout.settlement.amountExceedsDue");
        assertThat(id(ownerTenant, "SELECT count(*) FROM financial_movement")).isEqualTo(movementsBefore);
        assertThat(id(ownerTenant, "SELECT count(*) FROM event_payout_settlement")).isZero();
    }

    @Test
    void enforcesTheConstraintsOfTheTables() {
        List<Long> users = users(ownerTenant, 1);
        Long eventId = event(ownerTenant, "Prova", "0", users);
        Long userId = users.getFirst();
        sql(ownerTenant, "INSERT INTO payout_setting (valid_from, association_share_percent, rounding_step) VALUES ('2026-01-01', 20, 5)");

        assertRefused(ownerTenant, "INSERT INTO payout_setting (valid_from, association_share_percent, rounding_step) VALUES ('2026-01-01', 10, 1)");
        assertRefused(ownerTenant, "INSERT INTO payout_setting (valid_from, association_share_percent, rounding_step) VALUES ('2026-02-01', 120, 1)");
        assertRefused(ownerTenant, adjustment(eventId, userId, "0.5", "10", "'Motivo'"));
        assertRefused(ownerTenant, adjustment(eventId, userId, "NULL", "NULL", "'Motivo'"));
        assertRefused(ownerTenant, adjustment(eventId, userId, "1.5", "NULL", "'Motivo'"));
        assertRefused(ownerTenant, adjustment(eventId, userId, "NULL", "0", "'Motivo'"));
        assertRefused(ownerTenant, adjustment(eventId, userId, "0.5", "NULL", "'  '"));
        sql(ownerTenant, adjustment(eventId, userId, "0.5", "NULL", "'Motivo'"));
        assertRefused(ownerTenant, adjustment(eventId, userId, "NULL", "10", "'Motivo'"));

        // A deleted version frees its date, a deleted adjustment frees its participant.
        sql(ownerTenant, "UPDATE payout_setting SET deleted = TRUE");
        sql(ownerTenant, "UPDATE event_payout_adjustment SET deleted = TRUE");
        sql(ownerTenant, "INSERT INTO payout_setting (valid_from, association_share_percent, rounding_step) VALUES ('2026-01-01', 10, 1)");
        sql(ownerTenant, adjustment(eventId, userId, "NULL", "10", "'Motivo'"));
    }

    @Test
    void keepsTheParametersOfEachTenantInsideItsOwnSchema() {
        transactionExecutor.execute(ownerTenant, () ->
            payoutService.createSetting(new SettingRequest(LocalDate.of(2026, 1, 1), new BigDecimal("20"), new BigDecimal("5")), owner()));

        assertThat(transactionExecutor.execute(intruderTenant, () -> payoutService.findSettings(authentication(intruderTenant)))).isEmpty();
        assertThat(transactionExecutor.execute(ownerTenant, () -> payoutService.findSettings(owner()))).hasSize(1);
    }

    // ---------------------------------------------------------------------------------------------------------------

    private EventPayoutDTO simulate(Long eventId) {
        return transactionExecutor.execute(ownerTenant, () -> payoutService.simulate(eventId, null, null, owner()));
    }

    private static EventLineDTO line(EventPayoutDTO payout, Long userId) {
        return payout.lines().stream().filter(line -> line.userId().equals(userId)).findFirst().orElseThrow();
    }

    private List<Long> users(String tenant, int count) {
        String prefix = prefix();
        jdbcTemplate.update("INSERT INTO public.user_identity (keycloak_id, created_at) SELECT '" + prefix + "' || g, CURRENT_TIMESTAMP"
            + " FROM generate_series(1, " + count + ") g ON CONFLICT DO NOTHING");
        sql(tenant, "INSERT INTO app_user (user_identity_id, keycloak_id, name, last_name, active) SELECT id, keycloak_id, 'Socio', keycloak_id, TRUE"
            + " FROM public.user_identity WHERE keycloak_id LIKE '" + prefix + "%'");
        return transactionExecutor.execute(tenant, () -> entityManager
            .createNativeQuery("SELECT id FROM app_user WHERE keycloak_id LIKE :prefix ORDER BY keycloak_id")
            .setParameter("prefix", prefix + "%")
            .getResultList().stream().map(value -> ((Number) value).longValue()).toList());
    }

    private Long event(String tenant, String name, String fee, List<Long> present) {
        sql(tenant, "INSERT INTO calendar_event (name, state, location, start_date, end_date, fee, deleted, insert_by, insert_date, edit_by, edit_date)"
            + " VALUES ('" + name + "', 'COMPLETE', 'Piazza', '2026-03-15 21:00:00+01', '2026-03-15 23:00:00+01', " + fee + ","
            + " FALSE, 'test', now(), 'test', now())");
        Long eventId = id(tenant, "SELECT MAX(id) FROM calendar_event");
        for (int index = 0; index < present.size(); index++) {
            sql(tenant, "INSERT INTO calendar_event_presence (event_id, user_id, display_order) VALUES (" + eventId + ", " + present.get(index) + ", " + index + ")");
        }
        return eventId;
    }

    private static String adjustment(Long eventId, Long userId, String factor, String fixedAmount, String reason) {
        return "INSERT INTO event_payout_adjustment (event_id, user_id, factor, fixed_amount, reason) VALUES ("
            + eventId + ", " + userId + ", " + factor + ", " + fixedAmount + ", " + reason + ")";
    }

    private void assertRefused(String tenant, String statement) {
        assertThatThrownBy(() -> sql(tenant, statement)).as(statement).isInstanceOf(RuntimeException.class);
    }

    private void sql(String tenant, String statement) {
        transactionExecutor.execute(tenant, () -> entityManager.createNativeQuery(statement).executeUpdate());
    }

    private Long id(String tenant, String query) {
        return transactionExecutor.execute(tenant, () -> ((Number) entityManager.createNativeQuery(query).getSingleResult()).longValue());
    }

    private String prefix() {
        return "payout-" + run + "-";
    }

    private static AccountRequest cashAccount() {
        return new AccountRequest("Cassa", null, FinancialAccountType.CASH, "EUR", null, null, 0, null, null, null);
    }

    private static MovementRequest income(Long accountId, Long eventId, String amount) {
        return new MovementRequest(
            accountId, null, eventId, FinancialDirection.INCOME, LocalDate.of(2026, 3, 20), null, new BigDecimal(amount), "Compenso dell'evento",
            null, null, null, null, null
        );
    }

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setFinanceEnabled(true);
        tenant.setCalendarEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("user-1");
        tenant.setInsertDate(now);
        tenant.setEditBy("user-1");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
    }

    private JwtAuthenticationToken owner() {
        return authentication(ownerTenant);
    }

    private static JwtAuthenticationToken authentication(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", tenant)
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
