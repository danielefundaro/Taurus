package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsDTO;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lo stesso giro di prove CRUD/IDOR su ogni risorsa: creata in un tenant, non deve essere
 * leggibile, modificabile né cancellabile dall'altro, e deve restare intatta nel proprio.
 *
 * <p>È il controllo sistematico chiesto dal runbook di test: una risorsa nuova si aggiunge a
 * {@code resources()} e porta con sé le stesse verifiche, invece di dipendere da un test scritto
 * a mano per ogni servizio.
 */
@IntegrationTest
// Le risorse arrivano dai servizi iniettati: la sorgente del test parametrizzato non può essere statica.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class TenantIsolationIT {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String ownerTenant = "isolation-owner-" + UUID.randomUUID();
    private final String intruderTenant = "isolation-intruder-" + UUID.randomUUID();

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private TracksService tracksService;

    @Autowired
    private AlbumsService albumsService;

    @Autowired
    private InstrumentsService instrumentsService;

    @Autowired
    private CalendarEventsService calendarEventsService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private FinanceService financeService;

    @Autowired
    private PreferencesService preferencesService;

    @Autowired
    private TenantsRepository tenantsRepository;

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
        } finally {
            provisioningService.dropSchema(intruderTenant);
            unregisterTenant(ownerTenant);
            unregisterTenant(intruderTenant);
        }
    }

    /**
     * Inventario ed economia chiedono un tenant registrato e attivo, con il modulo acceso:
     * provisionare il solo schema vale per le risorse comuni, non per loro.
     */
    private void registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setInventoryEnabled(true);
        tenant.setFinanceEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("test");
        tenant.setInsertDate(now);
        tenant.setEditBy("test");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRepository.save(tenant);
    }

    private void unregisterTenant(String code) {
        tenantsRepository.findByCodeAndDeletedFalse(code).ifPresent(tenantsRepository::delete);
    }

    /** Le risorse sotto controllo: nome, servizio e come si costruisce una riga minima valida. */
    private Stream<Resource<?>> resources() {
        return Stream.of(
            new Resource<>("tracks", port(tracksService), () -> {
                TracksDTO dto = new TracksDTO();
                dto.setName("Marcia sinfonica");
                return dto;
            }),
            new Resource<>("albums", port(albumsService), () -> {
                AlbumsDTO dto = new AlbumsDTO();
                dto.setName("Concerto di Natale");
                return dto;
            }),
            new Resource<>("instruments", port(instrumentsService), () -> {
                InstrumentsDTO dto = new InstrumentsDTO();
                dto.setName("Clarinetto");
                return dto;
            }),
            new Resource<>("preferences", port(preferencesService), () -> {
                PreferencesDTO dto = new PreferencesDTO();
                dto.setKey("isolation-probe");
                dto.setValue("1");
                return dto;
            }),
            new Resource<>("calendar events", port(calendarEventsService), () -> {
                CalendarEventsDTO dto = new CalendarEventsDTO();
                dto.setName("Concerto di primavera");
                dto.setStartDate(new Date());
                dto.setEndDate(new Date(System.currentTimeMillis() + 3_600_000));
                return dto;
            }),
            new Resource<>("inventory items", inventoryItems(), () -> new InventoryItemRequest(
                "INV-" + UUID.randomUUID(),
                "Leggio",
                null,
                1,
                BigDecimal.TEN,
                "EUR",
                InventoryCondition.GOOD,
                null,
                null,
                null,
                null
            )),
            new Resource<>("finance accounts", financeAccounts(), () -> new AccountRequest(
                "Cassa " + UUID.randomUUID(),
                null,
                FinancialAccountType.CASH,
                "EUR",
                null,
                null,
                null,
                null,
                null,
                null
            ))
        );
    }

    /** L'inventario non passa da {@code CommonService}: la porta la costruisce il test. */
    private Crud<InventoryItemRequest> inventoryItems() {
        return new Crud<>() {
            @Override
            public Long save(InventoryItemRequest request, AbstractAuthenticationToken token) {
                return inventoryService.createItem(request, token).id();
            }

            @Override
            public void update(Long id, InventoryItemRequest request, AbstractAuthenticationToken token) {
                inventoryService.updateItem(id, request, token);
            }

            // L'assenza si legge dall'elenco: il findItem di un id estraneo segnerebbe la
            // transazione come sola rollback, e il test non potrebbe più chiuderla.
            @Override
            public boolean exists(Long id, AbstractAuthenticationToken token) {
                return inventoryService.findItems(null, Pageable.unpaged(), token).stream()
                    .anyMatch(item -> id.equals(item.id()));
            }

            @Override
            public void delete(Long id, AbstractAuthenticationToken token) {
                inventoryService.deleteItem(id, token);
            }

            @Override
            public long countAll(AbstractAuthenticationToken token) {
                return inventoryService.findItems(null, Pageable.unpaged(), token).getTotalElements();
            }
        };
    }

    /** Lo stesso per l'economia, che ha le sue firme con il DTO della richiesta. */
    private Crud<AccountRequest> financeAccounts() {
        return new Crud<>() {
            @Override
            public Long save(AccountRequest request, AbstractAuthenticationToken token) {
                return financeService.createAccount(request, token).id();
            }

            @Override
            public void update(Long id, AccountRequest request, AbstractAuthenticationToken token) {
                financeService.updateAccount(id, request, token);
            }

            @Override
            public boolean exists(Long id, AbstractAuthenticationToken token) {
                return financeService.findAccounts(true, token).stream()
                    .anyMatch(account -> id.equals(account.id()));
            }

            @Override
            public void delete(Long id, AbstractAuthenticationToken token) {
                financeService.deleteAccount(id, token);
            }

            @Override
            public long countAll(AbstractAuthenticationToken token) {
                return financeService.findAccounts(true, token).size();
            }
        };
    }

    @ParameterizedTest(name = "{0} stays inside its tenant")
    @MethodSource("resources")
    void keepsEveryResourceInsideItsTenant(Resource<?> resource) {
        // Alcune tabelle nascono con i dati di riferimento del tenant: conta la variazione, non il totale.
        long intruderRowsBefore = countFor(resource, intruderTenant);
        Long id = create(resource);

        transactionExecutor.execute(intruderTenant, () -> {
            assertThat(resource.service().exists(id, authentication(intruderTenant))).isFalse();
        });
        assertThat(countFor(resource, intruderTenant)).isEqualTo(intruderRowsBefore);

        assertOwnerStillHas(resource, id);
    }

    @ParameterizedTest(name = "{0} cannot be updated from another tenant")
    @MethodSource("resources")
    void refusesToUpdateAResourceFromAnotherTenant(Resource<?> resource) {
        Long id = create(resource);

        // Il messaggio dipende dal servizio: conta che l'aggiornamento sia rifiutato.
        assertThatThrownBy(() -> updateFromIntruder(resource, id)).isInstanceOf(RuntimeException.class);

        assertOwnerStillHas(resource, id);
    }

    @ParameterizedTest(name = "{0} cannot be deleted from another tenant")
    @MethodSource("resources")
    void refusesToDeleteAResourceFromAnotherTenant(Resource<?> resource) {
        Long id = create(resource);

        // Il rifiuto può arrivare come errore o come cancellazione senza effetto: conta che la riga resti.
        try {
            transactionExecutor.execute(intruderTenant, () -> {
                resource.service().delete(id, authentication(intruderTenant));
            });
        } catch (RuntimeException expected) {
            // Rifiutata: il servizio decide il messaggio, la riga resta del proprietario.
        }

        assertOwnerStillHas(resource, id);
    }

    private <D> void assertOwnerStillHas(Resource<D> resource, Long id) {
        transactionExecutor.execute(ownerTenant, () -> {
            assertThat(resource.service().exists(id, authentication(ownerTenant))).isTrue();
        });
    }

    private <D> void updateFromIntruder(Resource<D> resource, Long id) {
        transactionExecutor.execute(intruderTenant, () -> {
            resource.service().update(id, resource.newRow().get(), authentication(intruderTenant));
        });
    }

    private <D> long countFor(Resource<D> resource, String tenant) {
        return transactionExecutor.execute(tenant, () -> resource.service().countAll(authentication(tenant)));
    }

    private <D> Long create(Resource<D> resource) {
        return transactionExecutor.execute(ownerTenant, () -> resource.service().save(resource.newRow().get(), authentication(ownerTenant)));
    }

    private JwtAuthenticationToken authentication(String tenantCode) {
        Instant now = Instant.now();
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenantCode)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    /** Una risorsa con il proprio servizio e il proprio modo di costruire una riga minima. */
    private record Resource<D>(String name, Crud<D> service, Supplier<D> newRow) {
        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Le sole operazioni usate dal controllo. Il repository ha due famiglie di servizi, con firme
     * quasi uguali ma senza un'interfaccia comune: questa porta le unisce per il test.
     */
    private interface Crud<D> {
        Long save(D dto, AbstractAuthenticationToken token);

        void update(Long id, D dto, AbstractAuthenticationToken token);

        boolean exists(Long id, AbstractAuthenticationToken token);

        void delete(Long id, AbstractAuthenticationToken token);

        long countAll(AbstractAuthenticationToken token);
    }

    private static <D extends CommonFieldsDTO> Crud<D> port(CommonService<?, D, ?> service) {
        return new Crud<D>() {
            @Override
            public Long save(D dto, AbstractAuthenticationToken token) {
                return service.save(dto, token).getId();
            }

            @Override
            public void update(Long id, D dto, AbstractAuthenticationToken token) {
                dto.setId(id);
                service.update(id, dto, token);
            }

            @Override
            public boolean exists(Long id, AbstractAuthenticationToken token) {
                return service.findOne(id, token).isPresent();
            }

            @Override
            public void delete(Long id, AbstractAuthenticationToken token) {
                service.delete(id, token);
            }

            @Override
            public long countAll(AbstractAuthenticationToken token) {
                return service.countAll(token);
            }
        };
    }

    private static <D extends CommonFieldsOpenSearchDTO> Crud<D> port(CommonOpenSearchService<?, D, ?> service) {
        return new Crud<D>() {
            @Override
            public Long save(D dto, AbstractAuthenticationToken token) {
                return service.save(dto, token).getId();
            }

            @Override
            public void update(Long id, D dto, AbstractAuthenticationToken token) {
                dto.setId(id);
                service.update(id, dto, token);
            }

            @Override
            public boolean exists(Long id, AbstractAuthenticationToken token) {
                return service.findOne(id, token).isPresent();
            }

            @Override
            public void delete(Long id, AbstractAuthenticationToken token) {
                service.delete(id, token);
            }

            @Override
            public long countAll(AbstractAuthenticationToken token) {
                // I servizi OpenSearch contano per criteri: un criterio vuoto è il conteggio dello schema.
                return service.count(null, token);
            }
        };
    }
}
