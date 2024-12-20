package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.MediaAsserts.*;
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
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
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
 * Integration tests for the {@link MediaResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class MediaResourceIT {

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

    private static final Long DEFAULT_ORDER_NUMBER = 1L;
    private static final Long UPDATED_ORDER_NUMBER = 2L;
    private static final Long SMALLER_ORDER_NUMBER = 1L - 1L;

    private static final String ENTITY_API_URL = "/api/media";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MediaRepository mediaRepository;

    @Autowired
    private MediaMapper mediaMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Media media;

    private Media insertedMedia;

    @Autowired
    private InstrumentsRepository instrumentsRepository;

    @Autowired
    private TracksRepository tracksRepository;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Media createEntity(EntityManager em) {
        Media media = new Media()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .orderNumber(DEFAULT_ORDER_NUMBER);
        // Add required entity
        Tracks tracks;
        tracks = em.insert(TracksResourceIT.createEntity()).block();
        media.setTrack(tracks);
        return media;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Media createUpdatedEntity(EntityManager em) {
        Media updatedMedia = new Media()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .orderNumber(UPDATED_ORDER_NUMBER);
        // Add required entity
        Tracks tracks;
        tracks = em.insert(TracksResourceIT.createUpdatedEntity()).block();
        updatedMedia.setTrack(tracks);
        return updatedMedia;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Media.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
        TracksResourceIT.deleteEntities(em);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        media = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedMedia != null) {
            mediaRepository.delete(insertedMedia).block();
            insertedMedia = null;
        }
        deleteEntities(em);
    }

    @Test
    void createMedia() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);
        var returnedMediaDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(MediaDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Media in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedMedia = mediaMapper.toEntity(returnedMediaDTO);
        assertMediaUpdatableFieldsEquals(returnedMedia, getPersistedMedia(returnedMedia));

        insertedMedia = returnedMedia;
    }

    @Test
    void createMediaWithExistingId() throws Exception {
        // Create the Media with an existing ID
        media.setId(1L);
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllMedia() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList
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
            .value(hasItem(media.getId().intValue()))
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
            .jsonPath("$.[*].orderNumber")
            .value(hasItem(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getMedia() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get the media
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, media.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(media.getId().intValue()))
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
            .jsonPath("$.orderNumber")
            .value(is(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getMediaByIdFiltering() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        Long id = media.getId();

        defaultMediaFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultMediaFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultMediaFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllMediaByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where deleted equals to
        defaultMediaFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllMediaByDeletedIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where deleted in
        defaultMediaFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllMediaByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where deleted is not null
        defaultMediaFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllMediaByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertBy equals to
        defaultMediaFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllMediaByInsertByIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertBy in
        defaultMediaFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllMediaByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertBy is not null
        defaultMediaFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllMediaByInsertByContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertBy contains
        defaultMediaFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllMediaByInsertByNotContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertBy does not contain
        defaultMediaFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllMediaByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate equals to
        defaultMediaFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllMediaByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate in
        defaultMediaFiltering("insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE, "insertDate.in=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllMediaByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate is not null
        defaultMediaFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllMediaByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate is greater than or equal to
        defaultMediaFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllMediaByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate is less than or equal to
        defaultMediaFiltering("insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE, "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE);
    }

    @Test
    void getAllMediaByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate is less than
        defaultMediaFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllMediaByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where insertDate is greater than
        defaultMediaFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllMediaByEditByIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editBy equals to
        defaultMediaFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllMediaByEditByIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editBy in
        defaultMediaFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllMediaByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editBy is not null
        defaultMediaFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllMediaByEditByContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editBy contains
        defaultMediaFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllMediaByEditByNotContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editBy does not contain
        defaultMediaFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllMediaByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate equals to
        defaultMediaFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllMediaByEditDateIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate in
        defaultMediaFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllMediaByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate is not null
        defaultMediaFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllMediaByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate is greater than or equal to
        defaultMediaFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllMediaByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate is less than or equal to
        defaultMediaFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllMediaByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate is less than
        defaultMediaFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllMediaByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where editDate is greater than
        defaultMediaFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllMediaByNameIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where name equals to
        defaultMediaFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllMediaByNameIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where name in
        defaultMediaFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllMediaByNameIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where name is not null
        defaultMediaFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllMediaByNameContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where name contains
        defaultMediaFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllMediaByNameNotContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where name does not contain
        defaultMediaFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllMediaByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where description equals to
        defaultMediaFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllMediaByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where description in
        defaultMediaFiltering("description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION, "description.in=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllMediaByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where description is not null
        defaultMediaFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllMediaByDescriptionContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where description contains
        defaultMediaFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllMediaByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where description does not contain
        defaultMediaFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    void getAllMediaByOrderNumberIsEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber equals to
        defaultMediaFiltering("orderNumber.equals=" + DEFAULT_ORDER_NUMBER, "orderNumber.equals=" + UPDATED_ORDER_NUMBER);
    }

    @Test
    void getAllMediaByOrderNumberIsInShouldWork() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber in
        defaultMediaFiltering(
            "orderNumber.in=" + DEFAULT_ORDER_NUMBER + "," + UPDATED_ORDER_NUMBER,
            "orderNumber.in=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllMediaByOrderNumberIsNullOrNotNull() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber is not null
        defaultMediaFiltering("orderNumber.specified=true", "orderNumber.specified=false");
    }

    @Test
    void getAllMediaByOrderNumberIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber is greater than or equal to
        defaultMediaFiltering(
            "orderNumber.greaterThanOrEqual=" + DEFAULT_ORDER_NUMBER,
            "orderNumber.greaterThanOrEqual=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllMediaByOrderNumberIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber is less than or equal to
        defaultMediaFiltering("orderNumber.lessThanOrEqual=" + DEFAULT_ORDER_NUMBER, "orderNumber.lessThanOrEqual=" + SMALLER_ORDER_NUMBER);
    }

    @Test
    void getAllMediaByOrderNumberIsLessThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber is less than
        defaultMediaFiltering("orderNumber.lessThan=" + UPDATED_ORDER_NUMBER, "orderNumber.lessThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllMediaByOrderNumberIsGreaterThanSomething() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        // Get all the mediaList where orderNumber is greater than
        defaultMediaFiltering("orderNumber.greaterThan=" + SMALLER_ORDER_NUMBER, "orderNumber.greaterThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllMediaByInstrumentIsEqualToSomething() {
        Instruments instrument = InstrumentsResourceIT.createEntity();
        instrumentsRepository.save(instrument).block();
        Long instrumentId = instrument.getId();
        media.setInstrumentId(instrumentId);
        insertedMedia = mediaRepository.save(media).block();
        // Get all the mediaList where instrument equals to instrumentId
        defaultMediaShouldBeFound("instrumentId.equals=" + instrumentId);

        // Get all the mediaList where instrument equals to (instrumentId + 1)
        defaultMediaShouldNotBeFound("instrumentId.equals=" + (instrumentId + 1));
    }

    @Test
    void getAllMediaByTrackIsEqualToSomething() {
        Tracks track = TracksResourceIT.createEntity();
        tracksRepository.save(track).block();
        Long trackId = track.getId();
        media.setTrackId(trackId);
        insertedMedia = mediaRepository.save(media).block();
        // Get all the mediaList where track equals to trackId
        defaultMediaShouldBeFound("trackId.equals=" + trackId);

        // Get all the mediaList where track equals to (trackId + 1)
        defaultMediaShouldNotBeFound("trackId.equals=" + (trackId + 1));
    }

    private void defaultMediaFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultMediaShouldBeFound(shouldBeFound);
        defaultMediaShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultMediaShouldBeFound(String filter) {
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
            .value(hasItem(media.getId().intValue()))
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
            .jsonPath("$.[*].orderNumber")
            .value(hasItem(DEFAULT_ORDER_NUMBER.intValue()));

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
    private void defaultMediaShouldNotBeFound(String filter) {
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
    void getNonExistingMedia() {
        // Get the media
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingMedia() throws Exception {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the media
        Media updatedMedia = mediaRepository.findById(media.getId()).block();
        updatedMedia
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .orderNumber(UPDATED_ORDER_NUMBER);
        MediaDTO mediaDTO = mediaMapper.toDto(updatedMedia);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, mediaDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedMediaToMatchAllProperties(updatedMedia);
    }

    @Test
    void putNonExistingMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, mediaDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateMediaWithPatch() throws Exception {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the media using partial update
        Media partialUpdatedMedia = new Media();
        partialUpdatedMedia.setId(media.getId());

        partialUpdatedMedia.editDate(UPDATED_EDIT_DATE).name(UPDATED_NAME).description(UPDATED_DESCRIPTION);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedMedia.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedMedia))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Media in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMediaUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedMedia, media), getPersistedMedia(media));
    }

    @Test
    void fullUpdateMediaWithPatch() throws Exception {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the media using partial update
        Media partialUpdatedMedia = new Media();
        partialUpdatedMedia.setId(media.getId());

        partialUpdatedMedia
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .orderNumber(UPDATED_ORDER_NUMBER);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedMedia.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedMedia))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Media in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertMediaUpdatableFieldsEquals(partialUpdatedMedia, getPersistedMedia(partialUpdatedMedia));
    }

    @Test
    void patchNonExistingMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, mediaDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamMedia() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        media.setId(longCount.incrementAndGet());

        // Create the Media
        MediaDTO mediaDTO = mediaMapper.toDto(media);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(mediaDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Media in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteMedia() {
        // Initialize the database
        insertedMedia = mediaRepository.save(media).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the media
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, media.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return mediaRepository.count().block();
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

    protected Media getPersistedMedia(Media media) {
        return mediaRepository.findById(media.getId()).block();
    }

    protected void assertPersistedMediaToMatchAllProperties(Media expectedMedia) {
        // Test fails because reactive api returns an empty object instead of null
        // assertMediaAllPropertiesEquals(expectedMedia, getPersistedMedia(expectedMedia));
        assertMediaUpdatableFieldsEquals(expectedMedia, getPersistedMedia(expectedMedia));
    }

    protected void assertPersistedMediaToMatchUpdatableProperties(Media expectedMedia) {
        // Test fails because reactive api returns an empty object instead of null
        // assertMediaAllUpdatablePropertiesEquals(expectedMedia, getPersistedMedia(expectedMedia));
        assertMediaUpdatableFieldsEquals(expectedMedia, getPersistedMedia(expectedMedia));
    }
}
