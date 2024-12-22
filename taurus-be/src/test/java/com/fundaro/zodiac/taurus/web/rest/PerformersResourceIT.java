package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.PerformersAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.PerformersRepository;
import com.fundaro.zodiac.taurus.service.dto.PerformersDTO;
import com.fundaro.zodiac.taurus.service.mapper.PerformersMapper;
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
 * Integration tests for the {@link PerformersResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class PerformersResourceIT {

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

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/performers";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PerformersRepository performersRepository;

    @Autowired
    private PerformersMapper performersMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Performers performers;

    private Performers insertedPerformers;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private MediaRepository mediaRepository;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Performers createEntity(EntityManager em) {
        Performers performers = new Performers()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .description(DEFAULT_DESCRIPTION);
        // Add required entity
        Instruments instruments;
        instruments = em.insert(InstrumentsResourceIT.createEntity()).block();
        performers.setInstrument(instruments);
        // Add required entity
        Media media;
        media = em.insert(MediaResourceIT.createEntity(em)).block();
        performers.setMedia(media);
        return performers;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Performers createUpdatedEntity(EntityManager em) {
        Performers updatedPerformers = new Performers()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .description(UPDATED_DESCRIPTION);
        // Add required entity
        Instruments instruments;
        instruments = em.insert(InstrumentsResourceIT.createUpdatedEntity()).block();
        updatedPerformers.setInstrument(instruments);
        // Add required entity
        Media media;
        media = em.insert(MediaResourceIT.createUpdatedEntity(em)).block();
        updatedPerformers.setMedia(media);
        return updatedPerformers;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Performers.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
        InstrumentsResourceIT.deleteEntities(em);
        MediaResourceIT.deleteEntities(em);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        performers = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedPerformers != null) {
            performersRepository.delete(insertedPerformers).block();
            insertedPerformers = null;
        }
        deleteEntities(em);
    }

    @Test
    void createPerformers() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);
        var returnedPerformersDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(PerformersDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Performers in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPerformers = performersMapper.toEntity(returnedPerformersDTO);
        assertPerformersUpdatableFieldsEquals(returnedPerformers, getPersistedPerformers(returnedPerformers));

        insertedPerformers = returnedPerformers;
    }

    @Test
    void createPerformersWithExistingId() throws Exception {
        // Create the Performers with an existing ID
        performers.setId(1L);
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllPerformers() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList
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
            .value(hasItem(performers.getId().intValue()))
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
            .jsonPath("$.[*].description")
            .value(hasItem(DEFAULT_DESCRIPTION));
    }

    @Test
    void getPerformers() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get the performers
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, performers.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(performers.getId().intValue()))
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
            .jsonPath("$.description")
            .value(is(DEFAULT_DESCRIPTION));
    }

    @Test
    void getPerformersByIdFiltering() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        Long id = performers.getId();

        defaultPerformersFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultPerformersFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultPerformersFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllPerformersByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where deleted equals to
        defaultPerformersFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllPerformersByDeletedIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where deleted in
        defaultPerformersFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllPerformersByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where deleted is not null
        defaultPerformersFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllPerformersByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertBy equals to
        defaultPerformersFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPerformersByInsertByIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertBy in
        defaultPerformersFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPerformersByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertBy is not null
        defaultPerformersFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllPerformersByInsertByContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertBy contains
        defaultPerformersFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPerformersByInsertByNotContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertBy does not contain
        defaultPerformersFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllPerformersByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate equals to
        defaultPerformersFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllPerformersByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate in
        defaultPerformersFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllPerformersByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate is not null
        defaultPerformersFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllPerformersByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate is greater than or equal to
        defaultPerformersFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllPerformersByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate is less than or equal to
        defaultPerformersFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllPerformersByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate is less than
        defaultPerformersFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPerformersByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where insertDate is greater than
        defaultPerformersFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPerformersByEditByIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editBy equals to
        defaultPerformersFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPerformersByEditByIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editBy in
        defaultPerformersFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPerformersByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editBy is not null
        defaultPerformersFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllPerformersByEditByContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editBy contains
        defaultPerformersFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPerformersByEditByNotContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editBy does not contain
        defaultPerformersFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllPerformersByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate equals to
        defaultPerformersFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPerformersByEditDateIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate in
        defaultPerformersFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPerformersByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate is not null
        defaultPerformersFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllPerformersByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate is greater than or equal to
        defaultPerformersFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPerformersByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate is less than or equal to
        defaultPerformersFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllPerformersByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate is less than
        defaultPerformersFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPerformersByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where editDate is greater than
        defaultPerformersFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPerformersByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where description equals to
        defaultPerformersFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllPerformersByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where description in
        defaultPerformersFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllPerformersByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where description is not null
        defaultPerformersFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllPerformersByDescriptionContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where description contains
        defaultPerformersFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllPerformersByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        // Get all the performersList where description does not contain
        defaultPerformersFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    @Test
    void getAllPerformersByInstrumentIsEqualToSomething() {
        Instruments instrument = InstrumentsResourceIT.createEntity();
        instrumentsRepository.save(instrument).block();
        Long instrumentId = instrument.getId();
        performers.setInstrumentId(instrumentId);
        insertedPerformers = performersRepository.save(performers).block();
        // Get all the performersList where instrument equals to instrumentId
        defaultPerformersShouldBeFound("instrumentId.equals=" + instrumentId);

        // Get all the performersList where instrument equals to (instrumentId + 1)
        defaultPerformersShouldNotBeFound("instrumentId.equals=" + (instrumentId + 1));
    }

    @Test
    void getAllPerformersByMediaIsEqualToSomething() {
        Media media = MediaResourceIT.createEntity(em);
        mediaRepository.save(media).block();
        Long mediaId = media.getId();
        performers.setMediaId(mediaId);
        insertedPerformers = performersRepository.save(performers).block();
        // Get all the performersList where media equals to mediaId
        defaultPerformersShouldBeFound("mediaId.equals=" + mediaId);

        // Get all the performersList where media equals to (mediaId + 1)
        defaultPerformersShouldNotBeFound("mediaId.equals=" + (mediaId + 1));
    }

    private void defaultPerformersFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultPerformersShouldBeFound(shouldBeFound);
        defaultPerformersShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultPerformersShouldBeFound(String filter) {
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
            .value(hasItem(performers.getId().intValue()))
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
    private void defaultPerformersShouldNotBeFound(String filter) {
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
    void getNonExistingPerformers() {
        // Get the performers
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingPerformers() throws Exception {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the performers
        Performers updatedPerformers = performersRepository.findById(performers.getId()).block();
        updatedPerformers
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .description(UPDATED_DESCRIPTION);
        PerformersDTO performersDTO = performersMapper.toDto(updatedPerformers);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, performersDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPerformersToMatchAllProperties(updatedPerformers);
    }

    @Test
    void putNonExistingPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, performersDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePerformersWithPatch() throws Exception {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the performers using partial update
        Performers partialUpdatedPerformers = new Performers();
        partialUpdatedPerformers.setId(performers.getId());

        partialUpdatedPerformers.deleted(UPDATED_DELETED).editBy(UPDATED_EDIT_BY);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPerformers.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPerformers))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Performers in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPerformersUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedPerformers, performers),
            getPersistedPerformers(performers)
        );
    }

    @Test
    void fullUpdatePerformersWithPatch() throws Exception {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the performers using partial update
        Performers partialUpdatedPerformers = new Performers();
        partialUpdatedPerformers.setId(performers.getId());

        partialUpdatedPerformers
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .description(UPDATED_DESCRIPTION);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPerformers.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPerformers))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Performers in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPerformersUpdatableFieldsEquals(partialUpdatedPerformers, getPersistedPerformers(partialUpdatedPerformers));
    }

    @Test
    void patchNonExistingPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, performersDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPerformers() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        performers.setId(longCount.incrementAndGet());

        // Create the Performers
        PerformersDTO performersDTO = performersMapper.toDto(performers);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(performersDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Performers in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePerformers() {
        // Initialize the database
        insertedPerformers = performersRepository.save(performers).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the performers
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, performers.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return performersRepository.count().block();
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

    protected Performers getPersistedPerformers(Performers performers) {
        return performersRepository.findById(performers.getId()).block();
    }

    protected void assertPersistedPerformersToMatchAllProperties(Performers expectedPerformers) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPerformersAllPropertiesEquals(expectedPerformers, getPersistedPerformers(expectedPerformers));
        assertPerformersUpdatableFieldsEquals(expectedPerformers, getPersistedPerformers(expectedPerformers));
    }

    protected void assertPersistedPerformersToMatchUpdatableProperties(Performers expectedPerformers) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPerformersAllUpdatablePropertiesEquals(expectedPerformers, getPersistedPerformers(expectedPerformers));
        assertPerformersUpdatableFieldsEquals(expectedPerformers, getPersistedPerformers(expectedPerformers));
    }
}
