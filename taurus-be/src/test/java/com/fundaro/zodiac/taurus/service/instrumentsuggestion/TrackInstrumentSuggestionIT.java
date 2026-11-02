package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.instrumentsuggestion.InstrumentSuggestionSource;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.Action;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.ApplyRequestDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.DecisionDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.NameDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentSuggestionDtos.SuggestionsDTO;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The instruments to add on PostgreSQL: saved with the parts of a PDF, kept by the save of the track and removed with
 * its parts, applied in one transaction, and the check on the instrument names, also under concurrency.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class TrackInstrumentSuggestionIT {

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;

    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantSchemaNameResolver schemaNameResolver;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantsRepository tenantsRegistry;
    @Autowired
    TracksService tracksService;
    @Autowired
    InstrumentsService instrumentsService;
    @Autowired
    TrackInstrumentSuggestionService service;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String tenant = "suggestions-" + UUID.randomUUID();
    private final String otherTenant = "suggestions-other-" + UUID.randomUUID();

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provision() {
        registerTenant(tenant);
        registerTenant(otherTenant);
        provisioningService.provision(tenant);
        provisioningService.provision(otherTenant);
        // A new tenant starts with the band catalogue: the cases here build the instruments they need.
        jdbcTemplate.update("DELETE FROM " + table(tenant, "instrument"));
        jdbcTemplate.update("DELETE FROM " + table(otherTenant, "instrument"));
    }

    @AfterEach
    void drop() {
        try {
            provisioningService.dropSchema(tenant);
            provisioningService.dropSchema(otherTenant);
        } finally {
            unregisterTenant(tenant);
            unregisterTenant(otherTenant);
        }
    }

    @Test
    void groupsTheNamesOfTheTrackAndProposesTheAssociationOfAnInstrumentCreatedMeanwhile() {
        Long trackId = trackWithParts(
            part("Clarinetto basso 1°", draft("Clarinetto basso 1°", "Clarinetto basso")),
            part("Clarinetto basso 2°", draft("Clarinetto basso 2°", "Clarinetto basso")),
            part("Arpa", draft("ARPA", "Arpa"))
        );

        SuggestionsDTO suggestions = find(trackId);

        assertThat(suggestions.names()).extracting(NameDTO::normalizedName).containsExactly("clarinetto basso", "arpa");
        NameDTO clarinet = suggestions.names().get(0);
        assertThat(clarinet.action()).isEqualTo(Action.CREATE);
        assertThat(clarinet.parts()).hasSize(2).allSatisfy(part -> {
            assertThat(part.hasInstruments()).isFalse();
            assertThat(part.possibleScore()).isFalse();
            assertThat(part.source()).isEqualTo(InstrumentSuggestionSource.PDF_TEXT);
        });

        createInstrument("Clarinetto basso");

        NameDTO again = find(trackId).names().get(0);
        assertThat(again.action()).isEqualTo(Action.ASSOCIATE);
        assertThat(again.candidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.name()).isEqualTo("Clarinetto basso");
            assertThat(candidate.usersCount()).isZero();
        });
    }

    @Test
    void createsTheInstrumentWithTheProposalOfTheCatalogueAndAssignsItToTheSelectedParts() {
        Long existing = createInstrument("Fagotto").getId();
        Long trackId = trackWithParts(
            part("Clarinetto basso 1°", draft("Clarinetto basso 1°", "Clarinetto basso")),
            partWith("Clarinetto basso e fagotto", existing, draft("Clarinetto basso e fagotto", "Clarinetto basso")),
            part("Clarinetto basso 3°", draft("Clarinetto basso 3°", "Clarinetto basso"))
        );
        List<Long> parts = partIds(trackId);
        SuggestionsDTO before = find(trackId);

        TracksDTO track = apply(trackId, before.trackVersion(), new DecisionDTO("clarinetto basso", Action.CREATE, "Clarinetto basso in Sib", null, List.of(parts.get(0), parts.get(1))));

        InstrumentsDTO created = instrumentNamed("Clarinetto basso in Sib");
        assertThat(created.getSoundProgram()).isEqualTo(71);
        assertThat(created.getTranspositionSemitones()).isEqualTo(-14);
        assertThat(track.getVersion()).isGreaterThan(before.trackVersion());
        List<SheetsMusicDTO> scores = ordered(track);
        assertThat(instrumentNames(scores.get(0))).containsExactly("Clarinetto basso in Sib");
        assertThat(instrumentNames(scores.get(1))).containsExactly("Fagotto", "Clarinetto basso in Sib");
        assertThat(scores.get(0).getNeedsReview()).isTrue();
        assertThat(scores.get(2).getInstruments()).isNullOrEmpty();
        assertThat(scores.get(2).getNeedsReview()).isFalse();
        assertThat(find(trackId).names()).isEmpty();
        assertThat(statuses(trackId)).containsExactlyInAnyOrder("ACCEPTED", "ACCEPTED", "IGNORED");
    }

    @Test
    void associatesAnExistingInstrumentAndIgnoresTheOtherNames() {
        Long horn = createInstrument("Corno 1°").getId();
        createInstrument("Corno 2°");
        Long trackId = trackWithParts(part("Corni", draft("Corno in Fa", "Corno in Fa")), part("Arpa", draft("Arpa", "Arpa")));
        SuggestionsDTO before = find(trackId);
        assertThat(before.names().get(0).action()).isEqualTo(Action.ASSOCIATE);
        assertThat(before.names().get(0).candidates()).extracting(candidate -> candidate.name()).containsExactly("Corno 1°", "Corno 2°");

        TracksDTO track = apply(
            trackId,
            before.trackVersion(),
            new DecisionDTO("corno in fa", Action.ASSOCIATE, null, horn, partIds(trackId).subList(0, 1)),
            new DecisionDTO("arpa", Action.IGNORE, null, null, null)
        );

        assertThat(instrumentNames(ordered(track).get(0))).containsExactly("Corno 1°");
        assertThat(find(trackId).names()).isEmpty();
        assertThat(statuses(trackId)).containsExactlyInAnyOrder("ACCEPTED", "IGNORED");
    }

    @Test
    void leavesTheNamesWithoutADecisionOpen() {
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")), part("Chitarra", draft("Chitarra", "Chitarra")));

        apply(trackId, find(trackId).trackVersion(), new DecisionDTO("arpa", Action.IGNORE, null, null, null));

        assertThat(find(trackId).names()).extracting(NameDTO::normalizedName).containsExactly("chitarra");
    }

    @Test
    void createsNothingWhenTheNameAlreadyExists() {
        createInstrument("Arpa");
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")), part("Chitarra", draft("Chitarra", "Chitarra")));
        SuggestionsDTO before = find(trackId);

        assertThatThrownBy(() -> apply(
            trackId,
            before.trackVersion(),
            new DecisionDTO("chitarra", Action.CREATE, "Chitarra", null, partIds(trackId).subList(1, 2)),
            new DecisionDTO("arpa", Action.CREATE, " arpa. ", null, partIds(trackId).subList(0, 1))
        )).isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("instrument.name.exists"));

        assertThat(instrumentNames()).containsExactly("Arpa");
        assertThat(find(trackId).names()).hasSize(2);
        assertThat(find(trackId).trackVersion()).isEqualTo(before.trackVersion());
    }

    @Test
    void refusesAChangedTrackAClosedNameAndAPartOfAnotherTrack() {
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")));
        Long otherTrack = trackWithParts(part("Arpa", draft("Arpa", "Arpa")));
        Long version = find(trackId).trackVersion();

        assertThatThrownBy(() -> apply(trackId, version - 1, new DecisionDTO("arpa", Action.IGNORE, null, null, null)))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("version.conflict"));
        assertThatThrownBy(() -> apply(trackId, version, new DecisionDTO("arpa", Action.CREATE, "Arpa", null, partIds(otherTrack))))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("track.score.notFound"));
        assertThatThrownBy(() -> apply(trackId, version, new DecisionDTO("chitarra", Action.IGNORE, null, null, null)))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("instrumentSuggestion.notOpen"));
        assertThat(instrumentNames()).isEmpty();
    }

    @Test
    void keepsTheNamesWhenTheTrackIsSavedAndRemovesThemWithTheParts() {
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")), part("Chitarra", draft("Chitarra", "Chitarra")));
        TracksDTO track = transactionExecutor.execute(tenant, () -> tracksService.findOne(trackId, archivist()).orElseThrow());
        List<SheetsMusicDTO> scores = ordered(track);
        scores.get(0).setNeedsReview(false);
        track.setScores(new LinkedHashSet<>(scores));

        transactionExecutor.execute(tenant, () -> tracksService.update(trackId, track, archivist()));
        assertThat(find(trackId).names()).hasSize(2);

        TracksDTO saved = transactionExecutor.execute(tenant, () -> tracksService.findOne(trackId, archivist()).orElseThrow());
        saved.setScores(new LinkedHashSet<>(ordered(saved).subList(1, 2)));
        transactionExecutor.execute(tenant, () -> tracksService.update(trackId, saved, archivist()));

        assertThat(find(trackId).names()).extracting(NameDTO::normalizedName).containsExactly("chitarra");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table("sheet_music_instrument_suggestion") + " WHERE deleted = TRUE", Long.class
        )).isEqualTo(1L);
    }

    @Test
    void marksAPartWithManyNamesAsAPossibleScore() {
        Long trackId = trackWithParts(part(
            "Partitura",
            draft("Arpa", "Arpa"),
            draft("Chitarra", "Chitarra"),
            draft("Celesta", "Marimba"),
            draft("Pianoforte", "Pianoforte"),
            draft("Contrabbasso", "Contrabbasso")
        ));

        assertThat(find(trackId).names()).hasSize(5).allSatisfy(name -> assertThat(name.parts()).singleElement().satisfies(part ->
            assertThat(part.possibleScore()).isTrue()
        ));
    }

    @Test
    void allowsOneOpenNameOfTheSameFormOnAPart() {
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")));
        Long part = partIds(trackId).get(0);

        assertThatThrownBy(() -> jdbcTemplate.update(
            "INSERT INTO " + table("sheet_music_instrument_suggestion") + " (sheet_music_id, read_text, proposed_name, normalized_name, source, status, " +
                "insert_date, insert_by, edit_date, edit_by) VALUES (?, 'Arpa', 'Arpa', 'arpa', 'OCR', 'OPEN', now(), 't', now(), 't')",
            part
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesInstrumentsWithTheNameOfAnotherOne() {
        InstrumentsDTO clarinet = createInstrument("Clarinetto in Sib");
        createInstrument("Clarinetto in Mib");
        createInstrument("Clarinetto 1°");
        createInstrument("Clarinetto 2°");

        assertThatThrownBy(() -> createInstrument("clarinetto  IN sib."))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("instrument.name.exists"));
        InstrumentsDTO rename = new InstrumentsDTO();
        rename.setId(clarinet.getId());
        rename.setName("Clarinetto 1°");
        assertThatThrownBy(() -> transactionExecutor.execute(tenant, () -> instrumentsService.partialUpdate(clarinet.getId(), rename, archivist())))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("instrument.name.exists"));

        rename.setName("Clarinetto in Sib.");
        transactionExecutor.execute(tenant, () -> instrumentsService.partialUpdate(clarinet.getId(), rename, archivist()));
        createInstrument("Arpa");
        transactionExecutor.execute(otherTenant, () -> instrumentsService.save(instrument("Arpa"), archivist(otherTenant)));
    }

    @Test
    void letsOnlyOneOfTwoConcurrentCreationsOfTheSameNameSucceed() throws Exception {
        List<Object> outcomes = inParallel(2, () -> {
            try {
                return transactionExecutor.execute(tenant, () -> instrumentsService.save(instrument("Arpa"), archivist())).getId();
            } catch (RequestAlertException error) {
                return error.getErrorKey();
            }
        });

        assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn("instrument.name.exists"::equals).hasSize(1);
        assertThat(instrumentNames()).containsExactly("Arpa");
    }

    @Test
    void keepsTheNamesOfEachTenantApart() {
        Long trackId = trackWithParts(part("Arpa", draft("Arpa", "Arpa")));

        assertThatThrownBy(() -> transactionExecutor.execute(otherTenant, () -> service.find(trackId)))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("id.notFound"));
        assertThat(find(trackId).names()).hasSize(1);
    }

    private SuggestionsDTO find(Long trackId) {
        return transactionExecutor.execute(tenant, () -> service.find(trackId));
    }

    private TracksDTO apply(Long trackId, Long version, DecisionDTO... decisions) {
        return transactionExecutor.execute(tenant, () -> service.apply(trackId, new ApplyRequestDTO(version, List.of(decisions)), archivist()));
    }

    private Long trackWithParts(SheetsMusicDTO... parts) {
        return transactionExecutor.execute(tenant, () -> {
            TracksDTO track = new TracksDTO();
            track.setName("Marcia");
            TracksDTO saved = tracksService.save(track, archivist());
            tracksService.appendScores(saved.getId(), List.of(parts), archivist());
            return saved.getId();
        });
    }

    private static SheetsMusicDTO part(String description, InstrumentSuggestionDraft... drafts) {
        SheetsMusicDTO part = new SheetsMusicDTO();
        part.setDescription(description);
        part.setInstrumentSuggestions(List.of(drafts));
        return part;
    }

    private static SheetsMusicDTO partWith(String description, Long instrumentId, InstrumentSuggestionDraft... drafts) {
        SheetsMusicDTO part = part(description, drafts);
        ChildrenEntitiesDTO instrument = new ChildrenEntitiesDTO();
        instrument.setIndex(instrumentId);
        instrument.setOrder(1L);
        part.setInstruments(Set.of(instrument));
        part.setNeedsReview(true);
        return part;
    }

    private static InstrumentSuggestionDraft draft(String line, String proposedName) {
        return new InstrumentSuggestionDraft(
            line,
            proposedName,
            InstrumentNames.uniqueKey(proposedName),
            InstrumentPlaybackSuggester.match(proposedName).map(InstrumentPlaybackSuggester.Match::keyword).orElse(null),
            InstrumentSuggestionSource.PDF_TEXT
        );
    }

    private List<Long> partIds(Long trackId) {
        return transactionExecutor.execute(tenant, () -> ordered(tracksService.findOne(trackId, archivist()).orElseThrow()))
            .stream()
            .map(SheetsMusicDTO::getId)
            .toList();
    }

    private static List<SheetsMusicDTO> ordered(TracksDTO track) {
        return track.getScores().stream().sorted(Comparator.comparing(SheetsMusicDTO::getOrder)).toList();
    }

    private static List<String> instrumentNames(SheetsMusicDTO part) {
        return part.getInstruments().stream().sorted(Comparator.comparing(ChildrenEntitiesDTO::getOrder)).map(ChildrenEntitiesDTO::getName).toList();
    }

    private List<String> statuses(Long trackId) {
        String ids = partIds(trackId).stream().map(String::valueOf).collect(Collectors.joining(","));
        return jdbcTemplate.queryForList(
            "SELECT status FROM " + table("sheet_music_instrument_suggestion") + " WHERE deleted = FALSE AND sheet_music_id IN (" + ids + ")",
            String.class
        );
    }

    private InstrumentsDTO createInstrument(String name) {
        return transactionExecutor.execute(tenant, () -> instrumentsService.save(instrument(name), archivist()));
    }

    private InstrumentsDTO instrumentNamed(String name) {
        return transactionExecutor.execute(tenant, () -> instrumentsService.findEntitiesByCriteria(null, Pageable.ofSize(100), archivist()))
            .getContent()
            .stream()
            .filter(instrument -> name.equals(instrument.getName()))
            .findFirst()
            .orElseThrow();
    }

    private List<String> instrumentNames() {
        return jdbcTemplate.queryForList("SELECT name FROM " + table("instrument") + " WHERE deleted = FALSE ORDER BY name", String.class);
    }

    private static InstrumentsDTO instrument(String name) {
        InstrumentsDTO instrument = new InstrumentsDTO();
        instrument.setName(name);
        return instrument;
    }

    private String table(String name) {
        return table(tenant, name);
    }

    private String table(String code, String name) {
        return "\"" + schemaNameResolver.resolve(code).replace("\"", "\"\"") + "\"." + name;
    }

    private List<Object> inParallel(int workers, Callable<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants value = new Tenants();
        value.setName(code);
        value.setCode(code);
        value.setActive(true);
        value.setDeleted(false);
        value.setInsertBy("user-1");
        value.setInsertDate(now);
        value.setEditBy("user-1");
        value.setEditDate(now);
        value.setEntityVersion(0L);
        tenantsRegistry.save(value);
    }

    private void unregisterTenant(String code) {
        tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
    }

    private JwtAuthenticationToken archivist() {
        return archivist(tenant);
    }

    private static JwtAuthenticationToken archivist(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenant)
            .claim("given_name", "Mario")
            .claim("family_name", "Rossi")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ARCHIVIST")));
    }
}
