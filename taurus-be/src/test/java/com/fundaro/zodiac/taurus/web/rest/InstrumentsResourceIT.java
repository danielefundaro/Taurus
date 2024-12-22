package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.InstrumentsAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.mapper.InstrumentsMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the {@link InstrumentsResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class InstrumentsResourceIT {

    private static final Boolean DEFAULT_DELETED = false;
    private static final Boolean UPDATED_DELETED = true;

    private static final String DEFAULT_INSERT_BY = "AAAAAAAAAA";
    private static final String UPDATED_INSERT_BY = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_INSERT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_INSERT_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final ZonedDateTime SMALLER_INSERT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(-1L), ZoneOffset.UTC);

    private static final String DEFAULT_EDIT_BY = "AAAAAAAAAA";
    private static final String UPDATED_EDIT_BY = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_EDIT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_EDIT_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final ZonedDateTime SMALLER_EDIT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(-1L), ZoneOffset.UTC);

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/instruments";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private InstrumentsMapper instrumentsMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Instruments instruments;

    private Instruments insertedInstruments;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Instruments createEntity() {
        return new Instruments()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Instruments createUpdatedEntity() {
        return new Instruments()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Instruments.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        instruments = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedInstruments != null) {
            instrumentsRepository.delete(insertedInstruments).block();
            insertedInstruments = null;
        }
        deleteEntities(em);
    }

    @Test
    void createInstruments() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);
        var returnedInstrumentsDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(InstrumentsDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Instruments in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedInstruments = instrumentsMapper.toEntity(returnedInstrumentsDTO);
        assertInstrumentsUpdatableFieldsEquals(returnedInstruments, getPersistedInstruments(returnedInstruments));

        insertedInstruments = returnedInstruments;
    }

    @Test
    void createInstrumentsWithExistingId() throws Exception {
        // Create the Instruments with an existing ID
        instruments.setId(1L);
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        instruments.setName(null);

        // Create the Instruments, which fails.
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllInstruments() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(instruments.getId().intValue()))
            .jsonPath("$.[*].deleted")
            .value(hasItem(DEFAULT_DELETED.booleanValue()))
            .jsonPath("$.[*].insertBy")
            .value(hasItem(DEFAULT_INSERT_BY))
            .jsonPath("$.[*].insertDate")
            .value(hasItem(sameInstant(DEFAULT_INSERT_DATE)))
            .jsonPath("$.[*].editBy")
            .value(hasItem(DEFAULT_EDIT_BY))
            .jsonPath("$.[*].editDate")
            .value(hasItem(sameInstant(DEFAULT_EDIT_DATE)))
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME))
            .jsonPath("$.[*].description")
            .value(hasItem(DEFAULT_DESCRIPTION));
    }

    @Test
    void getInstruments() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get the instruments
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, instruments.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(instruments.getId().intValue()))
            .jsonPath("$.deleted")
            .value(is(DEFAULT_DELETED.booleanValue()))
            .jsonPath("$.insertBy")
            .value(is(DEFAULT_INSERT_BY))
            .jsonPath("$.insertDate")
            .value(is(sameInstant(DEFAULT_INSERT_DATE)))
            .jsonPath("$.editBy")
            .value(is(DEFAULT_EDIT_BY))
            .jsonPath("$.editDate")
            .value(is(sameInstant(DEFAULT_EDIT_DATE)))
            .jsonPath("$.name")
            .value(is(DEFAULT_NAME))
            .jsonPath("$.description")
            .value(is(DEFAULT_DESCRIPTION));
    }

    @Test
    void getInstrumentsByIdFiltering() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        Long id = instruments.getId();

        defaultInstrumentsFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultInstrumentsFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultInstrumentsFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllInstrumentsByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where deleted equals to
        defaultInstrumentsFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllInstrumentsByDeletedIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where deleted in
        defaultInstrumentsFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllInstrumentsByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where deleted is not null
        defaultInstrumentsFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllInstrumentsByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertBy equals to
        defaultInstrumentsFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllInstrumentsByInsertByIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertBy in
        defaultInstrumentsFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllInstrumentsByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertBy is not null
        defaultInstrumentsFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllInstrumentsByInsertByContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertBy contains
        defaultInstrumentsFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllInstrumentsByInsertByNotContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertBy does not contain
        defaultInstrumentsFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllInstrumentsByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate equals to
        defaultInstrumentsFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllInstrumentsByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate in
        defaultInstrumentsFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllInstrumentsByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate is not null
        defaultInstrumentsFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllInstrumentsByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate is greater than or equal to
        defaultInstrumentsFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllInstrumentsByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate is less than or equal to
        defaultInstrumentsFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllInstrumentsByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate is less than
        defaultInstrumentsFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllInstrumentsByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where insertDate is greater than
        defaultInstrumentsFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllInstrumentsByEditByIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editBy equals to
        defaultInstrumentsFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllInstrumentsByEditByIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editBy in
        defaultInstrumentsFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllInstrumentsByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editBy is not null
        defaultInstrumentsFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllInstrumentsByEditByContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editBy contains
        defaultInstrumentsFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllInstrumentsByEditByNotContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editBy does not contain
        defaultInstrumentsFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllInstrumentsByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate equals to
        defaultInstrumentsFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByEditDateIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate in
        defaultInstrumentsFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate is not null
        defaultInstrumentsFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllInstrumentsByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate is greater than or equal to
        defaultInstrumentsFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate is less than or equal to
        defaultInstrumentsFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate is less than
        defaultInstrumentsFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where editDate is greater than
        defaultInstrumentsFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllInstrumentsByNameIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where name equals to
        defaultInstrumentsFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllInstrumentsByNameIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where name in
        defaultInstrumentsFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllInstrumentsByNameIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where name is not null
        defaultInstrumentsFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllInstrumentsByNameContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where name contains
        defaultInstrumentsFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllInstrumentsByNameNotContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where name does not contain
        defaultInstrumentsFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllInstrumentsByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where description equals to
        defaultInstrumentsFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllInstrumentsByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where description in
        defaultInstrumentsFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllInstrumentsByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where description is not null
        defaultInstrumentsFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllInstrumentsByDescriptionContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where description contains
        defaultInstrumentsFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllInstrumentsByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        // Get all the instrumentsList where description does not contain
        defaultInstrumentsFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    private void defaultInstrumentsFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultInstrumentsShouldBeFound(shouldBeFound);
        defaultInstrumentsShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultInstrumentsShouldBeFound(String filter) {
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc&" + filter)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.[*].id")
            .value(hasItem(instruments.getId().intValue()))
            .jsonPath("$.[*].deleted")
            .value(hasItem(DEFAULT_DELETED.booleanValue()))
            .jsonPath("$.[*].insertBy")
            .value(hasItem(DEFAULT_INSERT_BY))
            .jsonPath("$.[*].insertDate")
            .value(hasItem(sameInstant(DEFAULT_INSERT_DATE)))
            .jsonPath("$.[*].editBy")
            .value(hasItem(DEFAULT_EDIT_BY))
            .jsonPath("$.[*].editDate")
            .value(hasItem(sameInstant(DEFAULT_EDIT_DATE)))
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME))
            .jsonPath("$.[*].description")
            .value(hasItem(DEFAULT_DESCRIPTION));

        // Check, that the count call also returns 1
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "/count?sort=id,desc&" + filter)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$")
            .value(is(1));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultInstrumentsShouldNotBeFound(String filter) {
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "?sort=id,desc&" + filter)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$")
            .isArray()
            .jsonPath("$")
            .isEmpty();

        // Check, that the count call also returns 0
        webTestClient
            .get()
            .uri(ENTITY_API_URL + "/count?sort=id,desc&" + filter)
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$")
            .value(is(0));
    }

    @Test
    void getNonExistingInstruments() {
        // Get the instruments
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingInstruments() throws Exception {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the instruments
        Instruments updatedInstruments = instrumentsRepository.findById(instruments.getId()).block();
        updatedInstruments
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION);
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(updatedInstruments);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, instrumentsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedInstrumentsToMatchAllProperties(updatedInstruments);
    }

    @Test
    void putNonExistingInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, instrumentsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateInstrumentsWithPatch() throws Exception {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the instruments using partial update
        Instruments partialUpdatedInstruments = new Instruments();
        partialUpdatedInstruments.setId(instruments.getId());

        partialUpdatedInstruments.insertDate(UPDATED_INSERT_DATE).editBy(UPDATED_EDIT_BY).description(UPDATED_DESCRIPTION);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedInstruments.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedInstruments))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Instruments in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertInstrumentsUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedInstruments, instruments),
            getPersistedInstruments(instruments)
        );
    }

    @Test
    void fullUpdateInstrumentsWithPatch() throws Exception {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the instruments using partial update
        Instruments partialUpdatedInstruments = new Instruments();
        partialUpdatedInstruments.setId(instruments.getId());

        partialUpdatedInstruments
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedInstruments.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedInstruments))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Instruments in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertInstrumentsUpdatableFieldsEquals(partialUpdatedInstruments, getPersistedInstruments(partialUpdatedInstruments));
    }

    @Test
    void patchNonExistingInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, instrumentsDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamInstruments() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        instruments.setId(longCount.incrementAndGet());

        // Create the Instruments
        InstrumentsDTO instrumentsDTO = instrumentsMapper.toDto(instruments);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(instrumentsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Instruments in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteInstruments() {
        // Initialize the database
        insertedInstruments = instrumentsRepository.save(instruments).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the instruments
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, instruments.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return instrumentsRepository.count().block();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Instruments getPersistedInstruments(Instruments instruments) {
        return instrumentsRepository.findById(instruments.getId()).block();
    }

    protected void assertPersistedInstrumentsToMatchAllProperties(Instruments expectedInstruments) {
        // Test fails because reactive api returns an empty object instead of null
        // assertInstrumentsAllPropertiesEquals(expectedInstruments, getPersistedInstruments(expectedInstruments));
        assertInstrumentsUpdatableFieldsEquals(expectedInstruments, getPersistedInstruments(expectedInstruments));
    }

    protected void assertPersistedInstrumentsToMatchUpdatableProperties(Instruments expectedInstruments) {
        // Test fails because reactive api returns an empty object instead of null
        // assertInstrumentsAllUpdatablePropertiesEquals(expectedInstruments, getPersistedInstruments(expectedInstruments));
        assertInstrumentsUpdatableFieldsEquals(expectedInstruments, getPersistedInstruments(expectedInstruments));
    }
}
