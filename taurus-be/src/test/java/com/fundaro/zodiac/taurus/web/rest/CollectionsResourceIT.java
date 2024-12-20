package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.CollectionsAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.repository.AlbumsRepository;
import com.fundaro.zodiac.taurus.repository.CollectionsRepository;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.dto.CollectionsDTO;
import com.fundaro.zodiac.taurus.service.mapper.CollectionsMapper;
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
 * Integration tests for the {@link CollectionsResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class CollectionsResourceIT {

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

    private static final Long DEFAULT_ORDER_NUMBER = 1L;
    private static final Long UPDATED_ORDER_NUMBER = 2L;
    private static final Long SMALLER_ORDER_NUMBER = 1L - 1L;

    private static final String ENTITY_API_URL = "/api/collections";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private CollectionsRepository collectionsRepository;

    @Autowired
    private CollectionsMapper collectionsMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Collections collections;

    private Collections insertedCollections;

    @Autowired
    private AlbumsRepository albumsRepository;

    @Autowired
    private TracksRepository tracksRepository;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Collections createEntity(EntityManager em) {
        Collections collections = new Collections()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .orderNumber(DEFAULT_ORDER_NUMBER);
        // Add required entity
        Albums albums;
        albums = em.insert(AlbumsResourceIT.createEntity()).block();
        collections.setAlbum(albums);
        // Add required entity
        Tracks tracks;
        tracks = em.insert(TracksResourceIT.createEntity()).block();
        collections.setTrack(tracks);
        return collections;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Collections createUpdatedEntity(EntityManager em) {
        Collections updatedCollections = new Collections()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .orderNumber(UPDATED_ORDER_NUMBER);
        // Add required entity
        Albums albums;
        albums = em.insert(AlbumsResourceIT.createUpdatedEntity()).block();
        updatedCollections.setAlbum(albums);
        // Add required entity
        Tracks tracks;
        tracks = em.insert(TracksResourceIT.createUpdatedEntity()).block();
        updatedCollections.setTrack(tracks);
        return updatedCollections;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Collections.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
        AlbumsResourceIT.deleteEntities(em);
        TracksResourceIT.deleteEntities(em);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        collections = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedCollections != null) {
            collectionsRepository.delete(insertedCollections).block();
            insertedCollections = null;
        }
        deleteEntities(em);
    }

    @Test
    void createCollections() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);
        var returnedCollectionsDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(CollectionsDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Collections in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedCollections = collectionsMapper.toEntity(returnedCollectionsDTO);
        assertCollectionsUpdatableFieldsEquals(returnedCollections, getPersistedCollections(returnedCollections));

        insertedCollections = returnedCollections;
    }

    @Test
    void createCollectionsWithExistingId() throws Exception {
        // Create the Collections with an existing ID
        collections.setId(1L);
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void getAllCollections() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList
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
            .value(hasItem(collections.getId().intValue()))
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
            .jsonPath("$.[*].orderNumber")
            .value(hasItem(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getCollections() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get the collections
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, collections.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(collections.getId().intValue()))
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
            .jsonPath("$.orderNumber")
            .value(is(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getCollectionsByIdFiltering() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        Long id = collections.getId();

        defaultCollectionsFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultCollectionsFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultCollectionsFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllCollectionsByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where deleted equals to
        defaultCollectionsFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllCollectionsByDeletedIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where deleted in
        defaultCollectionsFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllCollectionsByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where deleted is not null
        defaultCollectionsFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllCollectionsByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertBy equals to
        defaultCollectionsFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllCollectionsByInsertByIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertBy in
        defaultCollectionsFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllCollectionsByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertBy is not null
        defaultCollectionsFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllCollectionsByInsertByContainsSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertBy contains
        defaultCollectionsFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllCollectionsByInsertByNotContainsSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertBy does not contain
        defaultCollectionsFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllCollectionsByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate equals to
        defaultCollectionsFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllCollectionsByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate in
        defaultCollectionsFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllCollectionsByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate is not null
        defaultCollectionsFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllCollectionsByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate is greater than or equal to
        defaultCollectionsFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllCollectionsByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate is less than or equal to
        defaultCollectionsFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllCollectionsByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate is less than
        defaultCollectionsFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllCollectionsByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where insertDate is greater than
        defaultCollectionsFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllCollectionsByEditByIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editBy equals to
        defaultCollectionsFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllCollectionsByEditByIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editBy in
        defaultCollectionsFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllCollectionsByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editBy is not null
        defaultCollectionsFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllCollectionsByEditByContainsSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editBy contains
        defaultCollectionsFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllCollectionsByEditByNotContainsSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editBy does not contain
        defaultCollectionsFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllCollectionsByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate equals to
        defaultCollectionsFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByEditDateIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate in
        defaultCollectionsFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate is not null
        defaultCollectionsFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllCollectionsByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate is greater than or equal to
        defaultCollectionsFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate is less than or equal to
        defaultCollectionsFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate is less than
        defaultCollectionsFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where editDate is greater than
        defaultCollectionsFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllCollectionsByOrderNumberIsEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber equals to
        defaultCollectionsFiltering("orderNumber.equals=" + DEFAULT_ORDER_NUMBER, "orderNumber.equals=" + UPDATED_ORDER_NUMBER);
    }

    @Test
    void getAllCollectionsByOrderNumberIsInShouldWork() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber in
        defaultCollectionsFiltering(
            "orderNumber.in=" + DEFAULT_ORDER_NUMBER + "," + UPDATED_ORDER_NUMBER,
            "orderNumber.in=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllCollectionsByOrderNumberIsNullOrNotNull() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber is not null
        defaultCollectionsFiltering("orderNumber.specified=true", "orderNumber.specified=false");
    }

    @Test
    void getAllCollectionsByOrderNumberIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber is greater than or equal to
        defaultCollectionsFiltering(
            "orderNumber.greaterThanOrEqual=" + DEFAULT_ORDER_NUMBER,
            "orderNumber.greaterThanOrEqual=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllCollectionsByOrderNumberIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber is less than or equal to
        defaultCollectionsFiltering(
            "orderNumber.lessThanOrEqual=" + DEFAULT_ORDER_NUMBER,
            "orderNumber.lessThanOrEqual=" + SMALLER_ORDER_NUMBER
        );
    }

    @Test
    void getAllCollectionsByOrderNumberIsLessThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber is less than
        defaultCollectionsFiltering("orderNumber.lessThan=" + UPDATED_ORDER_NUMBER, "orderNumber.lessThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllCollectionsByOrderNumberIsGreaterThanSomething() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        // Get all the collectionsList where orderNumber is greater than
        defaultCollectionsFiltering("orderNumber.greaterThan=" + SMALLER_ORDER_NUMBER, "orderNumber.greaterThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllCollectionsByAlbumIsEqualToSomething() {
        Albums album = AlbumsResourceIT.createEntity();
        albumsRepository.save(album).block();
        Long albumId = album.getId();
        collections.setAlbumId(albumId);
        insertedCollections = collectionsRepository.save(collections).block();
        // Get all the collectionsList where album equals to albumId
        defaultCollectionsShouldBeFound("albumId.equals=" + albumId);

        // Get all the collectionsList where album equals to (albumId + 1)
        defaultCollectionsShouldNotBeFound("albumId.equals=" + (albumId + 1));
    }

    @Test
    void getAllCollectionsByTrackIsEqualToSomething() {
        Tracks track = TracksResourceIT.createEntity();
        tracksRepository.save(track).block();
        Long trackId = track.getId();
        collections.setTrackId(trackId);
        insertedCollections = collectionsRepository.save(collections).block();
        // Get all the collectionsList where track equals to trackId
        defaultCollectionsShouldBeFound("trackId.equals=" + trackId);

        // Get all the collectionsList where track equals to (trackId + 1)
        defaultCollectionsShouldNotBeFound("trackId.equals=" + (trackId + 1));
    }

    private void defaultCollectionsFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultCollectionsShouldBeFound(shouldBeFound);
        defaultCollectionsShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultCollectionsShouldBeFound(String filter) {
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
            .value(hasItem(collections.getId().intValue()))
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
    private void defaultCollectionsShouldNotBeFound(String filter) {
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
    void getNonExistingCollections() {
        // Get the collections
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingCollections() throws Exception {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the collections
        Collections updatedCollections = collectionsRepository.findById(collections.getId()).block();
        updatedCollections
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .orderNumber(UPDATED_ORDER_NUMBER);
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(updatedCollections);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, collectionsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedCollectionsToMatchAllProperties(updatedCollections);
    }

    @Test
    void putNonExistingCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, collectionsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateCollectionsWithPatch() throws Exception {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the collections using partial update
        Collections partialUpdatedCollections = new Collections();
        partialUpdatedCollections.setId(collections.getId());

        partialUpdatedCollections.editDate(UPDATED_EDIT_DATE).orderNumber(UPDATED_ORDER_NUMBER);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedCollections.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedCollections))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Collections in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCollectionsUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedCollections, collections),
            getPersistedCollections(collections)
        );
    }

    @Test
    void fullUpdateCollectionsWithPatch() throws Exception {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the collections using partial update
        Collections partialUpdatedCollections = new Collections();
        partialUpdatedCollections.setId(collections.getId());

        partialUpdatedCollections
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .orderNumber(UPDATED_ORDER_NUMBER);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedCollections.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedCollections))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Collections in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCollectionsUpdatableFieldsEquals(partialUpdatedCollections, getPersistedCollections(partialUpdatedCollections));
    }

    @Test
    void patchNonExistingCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, collectionsDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamCollections() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        collections.setId(longCount.incrementAndGet());

        // Create the Collections
        CollectionsDTO collectionsDTO = collectionsMapper.toDto(collections);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(collectionsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Collections in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteCollections() {
        // Initialize the database
        insertedCollections = collectionsRepository.save(collections).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the collections
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, collections.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return collectionsRepository.count().block();
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

    protected Collections getPersistedCollections(Collections collections) {
        return collectionsRepository.findById(collections.getId()).block();
    }

    protected void assertPersistedCollectionsToMatchAllProperties(Collections expectedCollections) {
        // Test fails because reactive api returns an empty object instead of null
        // assertCollectionsAllPropertiesEquals(expectedCollections, getPersistedCollections(expectedCollections));
        assertCollectionsUpdatableFieldsEquals(expectedCollections, getPersistedCollections(expectedCollections));
    }

    protected void assertPersistedCollectionsToMatchUpdatableProperties(Collections expectedCollections) {
        // Test fails because reactive api returns an empty object instead of null
        // assertCollectionsAllUpdatablePropertiesEquals(expectedCollections, getPersistedCollections(expectedCollections));
        assertCollectionsUpdatableFieldsEquals(expectedCollections, getPersistedCollections(expectedCollections));
    }
}
