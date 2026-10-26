package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Il modulo finanza su PostgreSQL: isolamento fra tenant, idempotenza di un trasferimento
 * ritentato, concorrenza ottimistica dichiarata dal client, filtri dell'elenco movimenti tradotti
 * in SQL, riporto annuale rigenerabile e rifiuto di un'identità senza tenant.
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
class FinanceIT {

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
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
    FinancialMovementRepository movementRepository;

    private final String ownerTenant = "finance-owner-" + UUID.randomUUID();
    private final String intruderTenant = "finance-intruder-" + UUID.randomUUID();

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
            unregisterTenant(ownerTenant);
            unregisterTenant(intruderTenant);
        }
    }

    @Test
    void keepsTheAccountingOfEachTenantInsideItsOwnSchema() {
        transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO account = financeService.createAccount(cashAccount("Cassa"), authentication(ownerTenant));
            financeService.createMovement(movement(account.id(), "Quota associativa", "Mario Rossi", null, null), authentication(ownerTenant));
        });

        transactionExecutor.execute(intruderTenant, () -> {
            assertThat(financeService.findAccounts(true, authentication(intruderTenant))).isEmpty();
            assertThat(
                financeService
                    .findMovements(null, null, null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20), authentication(intruderTenant))
                    .getContent()
            ).isEmpty();
        });

        transactionExecutor.execute(ownerTenant, () ->
            assertThat(
                financeService
                    .findMovements(null, null, null, null, null, null, null, null, null, null, null, PageRequest.of(0, 20), authentication(ownerTenant))
                    .getContent()
            ).hasSize(1)
        );
    }

    @Test
    void doesNotMoveTheMoneyTwiceWhenATransferIsRetried() {
        UUID requestKey = UUID.randomUUID();
        transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO source = financeService.createAccount(cashAccount("Cassa"), authentication(ownerTenant));
            AccountDTO destination = financeService.createAccount(cashAccount("Banca"), authentication(ownerTenant));
            TransferRequest request = new TransferRequest(
                source.id(),
                destination.id(),
                LocalDate.of(2026, 9, 1),
                null,
                new BigDecimal("50.00"),
                "Versamento in banca",
                null,
                requestKey
            );

            TransferDTO first = financeService.createTransfer(request, authentication(ownerTenant));
            TransferDTO retry = financeService.createTransfer(request, authentication(ownerTenant));

            assertThat(retry.transferGroup()).isEqualTo(first.transferGroup());
            assertThat(movementRepository.findAllByTransferGroupAndDeletedFalse(first.transferGroup())).hasSize(2);
            assertThat(financeService.accountBalance(source.id(), LocalDate.of(2026, 12, 31), authentication(ownerTenant)))
                .isEqualByComparingTo("-50.00");
        });
    }

    @Test
    void refusesAModificationBasedOnAVersionTheClientNoLongerHas() {
        // Il rifiuto sta in una transazione sua: un'eccezione dentro quella del test la segnerebbe
        // come sola-rollback, e il commit finale fallirebbe prima di arrivare all'assert.
        long[] identifiers = new long[2];
        long[] staleVersion = new long[1];
        transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO account = financeService.createAccount(cashAccount("Cassa"), authentication(ownerTenant));
            MovementDTO created = financeService.createMovement(
                movement(account.id(), "Quota associativa", "Mario Rossi", null, null),
                authentication(ownerTenant)
            );
            identifiers[0] = account.id();
            identifiers[1] = created.id();
            staleVersion[0] = created.version();
        });
        transactionExecutor.execute(ownerTenant, () ->
            financeService.updateMovement(
                identifiers[1],
                movement(identifiers[0], "Quota associativa 2026", "Mario Rossi", null, staleVersion[0]),
                authentication(ownerTenant)
            )
        );

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            financeService.updateMovement(
                identifiers[1],
                movement(identifiers[0], "Correzione tardiva", "Mario Rossi", null, staleVersion[0]),
                authentication(ownerTenant)
            )
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("finance.movement.versionConflict");
    }

    @Test
    void translatesTheListFiltersIntoSql() {
        transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO cash = financeService.createAccount(cashAccount("Cassa"), authentication(ownerTenant));
            AccountDTO bank = financeService.createAccount(cashAccount("Banca"), authentication(ownerTenant));
            financeService.createMovement(movement(cash.id(), "Quota 2026", "Mario Rossi", LocalDate.of(2026, 3, 1), null), authentication(ownerTenant));
            financeService.createMovement(movement(cash.id(), "Quota 2025", "Luisa Bianchi", LocalDate.of(2025, 3, 1), null), authentication(ownerTenant));
            financeService.createTransfer(
                new TransferRequest(cash.id(), bank.id(), LocalDate.of(2026, 4, 1), null, new BigDecimal("20.00"), "Versamento", null, null),
                authentication(ownerTenant)
            );

            assertThat(movements(2026, null, null)).extracting(MovementDTO::accountingYear).containsOnly(2026);
            assertThat(movements(2025, null, null)).extracting(MovementDTO::description).containsExactly("Quota 2025");
            assertThat(movements(null, FinancialMovementNature.TRANSFER, null)).hasSize(2);
            assertThat(movements(null, FinancialMovementNature.ORDINARY, "bianchi")).extracting(MovementDTO::description)
                .containsExactly("Quota 2025");
        });
    }

    @Test
    void regeneratesTheOpeningOfTheFollowingYearAfterALateCorrection() {
        transactionExecutor.execute(ownerTenant, () -> {
            AccountDTO account = financeService.createAccount(
                new AccountRequest(
                    "Cassa",
                    null,
                    FinancialAccountType.CASH,
                    "EUR",
                    null,
                    null,
                    0,
                    new BigDecimal("100.00"),
                    LocalDate.of(2025, 1, 1),
                    "Fondo cassa al passaggio di gestione"
                ),
                authentication(ownerTenant)
            );
            financeService.rollover(2025, authentication(ownerTenant));
            assertThat(financeService.accountBalance(account.id(), LocalDate.of(2026, 1, 1), authentication(ownerTenant)))
                .isEqualByComparingTo("100.00");

            // Correzione tardiva: un incasso del 2025 registrato dopo il riporto.
            financeService.createMovement(
                movement(account.id(), "Incasso dimenticato", "Mario Rossi", LocalDate.of(2025, 11, 30), null),
                authentication(ownerTenant)
            );
            financeService.rollover(2025, authentication(ownerTenant));

            assertThat(financeService.accountBalance(account.id(), LocalDate.of(2026, 1, 1), authentication(ownerTenant)))
                .isEqualByComparingTo("110.00");
            assertThat(financeService.findYear(2025, authentication(ownerTenant)).rolledOverAt()).isNotNull();
        });
    }

    @Test
    void refusesAnIdentityWithoutATenant() {
        Jwt withoutTenant = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();
        JwtAuthenticationToken token = new JwtAuthenticationToken(withoutTenant);

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () -> financeService.findAccounts(true, token)))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("auth.tenant.missing");
    }

    private List<MovementDTO> movements(Integer year, FinancialMovementNature nature, String counterparty) {
        return financeService
            .findMovements(null, null, year, null, null, null, null, nature, counterparty, null, null, PageRequest.of(0, 20), authentication(ownerTenant))
            .getContent();
    }

    private static AccountRequest cashAccount(String name) {
        return new AccountRequest(name, null, FinancialAccountType.CASH, "EUR", null, null, 0, null, null, null);
    }

    private static MovementRequest movement(Long accountId, String description, String counterparty, LocalDate date, Long version) {
        return new MovementRequest(
            accountId,
            null,
            null,
            FinancialDirection.INCOME,
            date == null ? LocalDate.of(2026, 9, 1) : date,
            null,
            new BigDecimal("10.00"),
            description,
            counterparty,
            null,
            null,
            null,
            version
        );
    }

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setFinanceEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("user-1");
        tenant.setInsertDate(now);
        tenant.setEditBy("user-1");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
    }

    private void unregisterTenant(String code) {
        tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
    }

    private static JwtAuthenticationToken authentication(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", tenant)
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
