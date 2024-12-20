package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.TracksAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.LkTrackTypeRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
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
 * Integration tests for the {@link TracksResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class TracksResourceIT {

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

    private static final String DEFAULT_COMPOSER = "AAAAAAAAAA";
    private static final String UPDATED_COMPOSER = "BBBBBBBBBB";

    private static final String DEFAULT_ARRANGER = "AAAAAAAAAA";
    private static final String UPDATED_ARRANGER = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/tracks";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private TracksRepository tracksRepository;

    @Autowired
    private TracksMapper tracksMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Tracks tracks;

    private Tracks insertedTracks;

    @Autowired
    private LkTrackTypeRepository lkTrackTypeRepository;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tracks createEntity() {
        return new Tracks()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .composer(DEFAULT_COMPOSER)
            .arranger(DEFAULT_ARRANGER);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Tracks createUpdatedEntity() {
        return new Tracks()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .composer(UPDATED_COMPOSER)
            .arranger(UPDATED_ARRANGER);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Tracks.class).block();
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
        tracks = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedTracks != null) {
            tracksRepository.delete(insertedTracks).block();
            insertedTracks = null;
        }
        deleteEntities(em);
    }

    @Test
    void createTracks() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);
        var returnedTracksDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(TracksDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Tracks in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedTracks = tracksMapper.toEntity(returnedTracksDTO);
        assertTracksUpdatableFieldsEquals(returnedTracks, getPersistedTracks(returnedTracks));

        insertedTracks = returnedTracks;
    }

    @Test
    void createTracksWithExistingId() throws Exception {
        // Create the Tracks with an existing ID
        tracks.setId(1L);
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        tracks.setName(null);

        // Create the Tracks, which fails.
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllTracks() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList
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
            .value(hasItem(tracks.getId().intValue()))
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
            .value(hasItem(DEFAULT_DESCRIPTION))
            .jsonPath("$.[*].composer")
            .value(hasItem(DEFAULT_COMPOSER))
            .jsonPath("$.[*].arranger")
            .value(hasItem(DEFAULT_ARRANGER));
    }

    @Test
    void getTracks() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get the tracks
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, tracks.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(tracks.getId().intValue()))
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
            .value(is(DEFAULT_DESCRIPTION))
            .jsonPath("$.composer")
            .value(is(DEFAULT_COMPOSER))
            .jsonPath("$.arranger")
            .value(is(DEFAULT_ARRANGER));
    }

    @Test
    void getTracksByIdFiltering() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        Long id = tracks.getId();

        defaultTracksFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultTracksFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultTracksFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllTracksByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where deleted equals to
        defaultTracksFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllTracksByDeletedIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where deleted in
        defaultTracksFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllTracksByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where deleted is not null
        defaultTracksFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllTracksByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertBy equals to
        defaultTracksFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllTracksByInsertByIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertBy in
        defaultTracksFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllTracksByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertBy is not null
        defaultTracksFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllTracksByInsertByContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertBy contains
        defaultTracksFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllTracksByInsertByNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertBy does not contain
        defaultTracksFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllTracksByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate equals to
        defaultTracksFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllTracksByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate in
        defaultTracksFiltering("insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE, "insertDate.in=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllTracksByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate is not null
        defaultTracksFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllTracksByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate is greater than or equal to
        defaultTracksFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllTracksByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate is less than or equal to
        defaultTracksFiltering("insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE, "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE);
    }

    @Test
    void getAllTracksByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate is less than
        defaultTracksFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllTracksByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where insertDate is greater than
        defaultTracksFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllTracksByEditByIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editBy equals to
        defaultTracksFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllTracksByEditByIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editBy in
        defaultTracksFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllTracksByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editBy is not null
        defaultTracksFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllTracksByEditByContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editBy contains
        defaultTracksFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllTracksByEditByNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editBy does not contain
        defaultTracksFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllTracksByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate equals to
        defaultTracksFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllTracksByEditDateIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate in
        defaultTracksFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllTracksByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate is not null
        defaultTracksFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllTracksByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate is greater than or equal to
        defaultTracksFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllTracksByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate is less than or equal to
        defaultTracksFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllTracksByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate is less than
        defaultTracksFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllTracksByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where editDate is greater than
        defaultTracksFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllTracksByNameIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where name equals to
        defaultTracksFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllTracksByNameIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where name in
        defaultTracksFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllTracksByNameIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where name is not null
        defaultTracksFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllTracksByNameContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where name contains
        defaultTracksFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllTracksByNameNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where name does not contain
        defaultTracksFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllTracksByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where description equals to
        defaultTracksFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllTracksByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where description in
        defaultTracksFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllTracksByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where description is not null
        defaultTracksFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllTracksByDescriptionContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where description contains
        defaultTracksFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllTracksByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where description does not contain
        defaultTracksFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    void getAllTracksByComposerIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where composer equals to
        defaultTracksFiltering("composer.equals=" + DEFAULT_COMPOSER, "composer.equals=" + UPDATED_COMPOSER);
    }

    @Test
    void getAllTracksByComposerIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where composer in
        defaultTracksFiltering("composer.in=" + DEFAULT_COMPOSER + "," + UPDATED_COMPOSER, "composer.in=" + UPDATED_COMPOSER);
    }

    @Test
    void getAllTracksByComposerIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where composer is not null
        defaultTracksFiltering("composer.specified=true", "composer.specified=false");
    }

    @Test
    void getAllTracksByComposerContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where composer contains
        defaultTracksFiltering("composer.contains=" + DEFAULT_COMPOSER, "composer.contains=" + UPDATED_COMPOSER);
    }

    @Test
    void getAllTracksByComposerNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where composer does not contain
        defaultTracksFiltering("composer.doesNotContain=" + UPDATED_COMPOSER, "composer.doesNotContain=" + DEFAULT_COMPOSER);
    }

    @Test
    void getAllTracksByArrangerIsEqualToSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where arranger equals to
        defaultTracksFiltering("arranger.equals=" + DEFAULT_ARRANGER, "arranger.equals=" + UPDATED_ARRANGER);
    }

    @Test
    void getAllTracksByArrangerIsInShouldWork() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where arranger in
        defaultTracksFiltering("arranger.in=" + DEFAULT_ARRANGER + "," + UPDATED_ARRANGER, "arranger.in=" + UPDATED_ARRANGER);
    }

    @Test
    void getAllTracksByArrangerIsNullOrNotNull() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where arranger is not null
        defaultTracksFiltering("arranger.specified=true", "arranger.specified=false");
    }

    @Test
    void getAllTracksByArrangerContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where arranger contains
        defaultTracksFiltering("arranger.contains=" + DEFAULT_ARRANGER, "arranger.contains=" + UPDATED_ARRANGER);
    }

    @Test
    void getAllTracksByArrangerNotContainsSomething() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        // Get all the tracksList where arranger does not contain
        defaultTracksFiltering("arranger.doesNotContain=" + UPDATED_ARRANGER, "arranger.doesNotContain=" + DEFAULT_ARRANGER);
    }

    @Test
    void getAllTracksByTypeIsEqualToSomething() {
        LkTrackType type = LkTrackTypeResourceIT.createEntity();
        lkTrackTypeRepository.save(type).block();
        Long typeId = type.getId();
        tracks.setTypeId(typeId);
        insertedTracks = tracksRepository.save(tracks).block();
        // Get all the tracksList where type equals to typeId
        defaultTracksShouldBeFound("typeId.equals=" + typeId);

        // Get all the tracksList where type equals to (typeId + 1)
        defaultTracksShouldNotBeFound("typeId.equals=" + (typeId + 1));
    }

    private void defaultTracksFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultTracksShouldBeFound(shouldBeFound);
        defaultTracksShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultTracksShouldBeFound(String filter) {
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
            .value(hasItem(tracks.getId().intValue()))
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
            .value(hasItem(DEFAULT_DESCRIPTION))
            .jsonPath("$.[*].composer")
            .value(hasItem(DEFAULT_COMPOSER))
            .jsonPath("$.[*].arranger")
            .value(hasItem(DEFAULT_ARRANGER));

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
    private void defaultTracksShouldNotBeFound(String filter) {
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
    void getNonExistingTracks() {
        // Get the tracks
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingTracks() throws Exception {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tracks
        Tracks updatedTracks = tracksRepository.findById(tracks.getId()).block();
        updatedTracks
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .composer(UPDATED_COMPOSER)
            .arranger(UPDATED_ARRANGER);
        TracksDTO tracksDTO = tracksMapper.toDto(updatedTracks);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, tracksDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedTracksToMatchAllProperties(updatedTracks);
    }

    @Test
    void putNonExistingTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, tracksDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateTracksWithPatch() throws Exception {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tracks using partial update
        Tracks partialUpdatedTracks = new Tracks();
        partialUpdatedTracks.setId(tracks.getId());

        partialUpdatedTracks.editBy(UPDATED_EDIT_BY).editDate(UPDATED_EDIT_DATE).name(UPDATED_NAME);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTracks.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTracks))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tracks in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTracksUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedTracks, tracks), getPersistedTracks(tracks));
    }

    @Test
    void fullUpdateTracksWithPatch() throws Exception {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the tracks using partial update
        Tracks partialUpdatedTracks = new Tracks();
        partialUpdatedTracks.setId(tracks.getId());

        partialUpdatedTracks
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .composer(UPDATED_COMPOSER)
            .arranger(UPDATED_ARRANGER);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedTracks.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedTracks))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Tracks in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertTracksUpdatableFieldsEquals(partialUpdatedTracks, getPersistedTracks(partialUpdatedTracks));
    }

    @Test
    void patchNonExistingTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, tracksDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamTracks() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        tracks.setId(longCount.incrementAndGet());

        // Create the Tracks
        TracksDTO tracksDTO = tracksMapper.toDto(tracks);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(tracksDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Tracks in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteTracks() {
        // Initialize the database
        insertedTracks = tracksRepository.save(tracks).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the tracks
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, tracks.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return tracksRepository.count().block();
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

    protected Tracks getPersistedTracks(Tracks tracks) {
        return tracksRepository.findById(tracks.getId()).block();
    }

    protected void assertPersistedTracksToMatchAllProperties(Tracks expectedTracks) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTracksAllPropertiesEquals(expectedTracks, getPersistedTracks(expectedTracks));
        assertTracksUpdatableFieldsEquals(expectedTracks, getPersistedTracks(expectedTracks));
    }

    protected void assertPersistedTracksToMatchUpdatableProperties(Tracks expectedTracks) {
        // Test fails because reactive api returns an empty object instead of null
        // assertTracksAllUpdatablePropertiesEquals(expectedTracks, getPersistedTracks(expectedTracks));
        assertTracksUpdatableFieldsEquals(expectedTracks, getPersistedTracks(expectedTracks));
    }
}
