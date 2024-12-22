package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.AlbumsAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.repository.AlbumsRepository;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
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
 * Integration tests for the {@link AlbumsResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class AlbumsResourceIT {

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

    private static final ZonedDateTime DEFAULT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final ZonedDateTime SMALLER_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(-1L), ZoneOffset.UTC);

    private static final String ENTITY_API_URL = "/api/albums";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AlbumsRepository albumsRepository;

    @Autowired
    private AlbumsMapper albumsMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Albums albums;

    private Albums insertedAlbums;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Albums createEntity() {
        return new Albums()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .date(DEFAULT_DATE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Albums createUpdatedEntity() {
        return new Albums()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .date(UPDATED_DATE);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Albums.class).block();
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
        albums = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedAlbums != null) {
            albumsRepository.delete(insertedAlbums).block();
            insertedAlbums = null;
        }
        deleteEntities(em);
    }

    @Test
    void createAlbums() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);
        var returnedAlbumsDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(AlbumsDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Albums in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedAlbums = albumsMapper.toEntity(returnedAlbumsDTO);
        assertAlbumsUpdatableFieldsEquals(returnedAlbums, getPersistedAlbums(returnedAlbums));

        insertedAlbums = returnedAlbums;
    }

    @Test
    void createAlbumsWithExistingId() throws Exception {
        // Create the Albums with an existing ID
        albums.setId(1L);
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        albums.setName(null);

        // Create the Albums, which fails.
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllAlbums() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList
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
            .value(hasItem(albums.getId().intValue()))
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
            .jsonPath("$.[*].date")
            .value(hasItem(sameInstant(DEFAULT_DATE)));
    }

    @Test
    void getAlbums() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get the albums
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, albums.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(albums.getId().intValue()))
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
            .jsonPath("$.date")
            .value(is(sameInstant(DEFAULT_DATE)));
    }

    @Test
    void getAlbumsByIdFiltering() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        Long id = albums.getId();

        defaultAlbumsFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultAlbumsFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultAlbumsFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllAlbumsByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where deleted equals to
        defaultAlbumsFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllAlbumsByDeletedIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where deleted in
        defaultAlbumsFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllAlbumsByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where deleted is not null
        defaultAlbumsFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllAlbumsByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertBy equals to
        defaultAlbumsFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllAlbumsByInsertByIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertBy in
        defaultAlbumsFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllAlbumsByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertBy is not null
        defaultAlbumsFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllAlbumsByInsertByContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertBy contains
        defaultAlbumsFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllAlbumsByInsertByNotContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertBy does not contain
        defaultAlbumsFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllAlbumsByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate equals to
        defaultAlbumsFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllAlbumsByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate in
        defaultAlbumsFiltering("insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE, "insertDate.in=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllAlbumsByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate is not null
        defaultAlbumsFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllAlbumsByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate is greater than or equal to
        defaultAlbumsFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllAlbumsByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate is less than or equal to
        defaultAlbumsFiltering("insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE, "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE);
    }

    @Test
    void getAllAlbumsByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate is less than
        defaultAlbumsFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllAlbumsByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where insertDate is greater than
        defaultAlbumsFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllAlbumsByEditByIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editBy equals to
        defaultAlbumsFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllAlbumsByEditByIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editBy in
        defaultAlbumsFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllAlbumsByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editBy is not null
        defaultAlbumsFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllAlbumsByEditByContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editBy contains
        defaultAlbumsFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllAlbumsByEditByNotContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editBy does not contain
        defaultAlbumsFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllAlbumsByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate equals to
        defaultAlbumsFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByEditDateIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate in
        defaultAlbumsFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate is not null
        defaultAlbumsFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllAlbumsByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate is greater than or equal to
        defaultAlbumsFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate is less than or equal to
        defaultAlbumsFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate is less than
        defaultAlbumsFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where editDate is greater than
        defaultAlbumsFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllAlbumsByNameIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where name equals to
        defaultAlbumsFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllAlbumsByNameIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where name in
        defaultAlbumsFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllAlbumsByNameIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where name is not null
        defaultAlbumsFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllAlbumsByNameContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where name contains
        defaultAlbumsFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllAlbumsByNameNotContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where name does not contain
        defaultAlbumsFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllAlbumsByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where description equals to
        defaultAlbumsFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllAlbumsByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where description in
        defaultAlbumsFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllAlbumsByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where description is not null
        defaultAlbumsFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllAlbumsByDescriptionContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where description contains
        defaultAlbumsFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllAlbumsByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where description does not contain
        defaultAlbumsFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    void getAllAlbumsByDateIsEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date equals to
        defaultAlbumsFiltering("date.equals=" + DEFAULT_DATE, "date.equals=" + UPDATED_DATE);
    }

    @Test
    void getAllAlbumsByDateIsInShouldWork() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date in
        defaultAlbumsFiltering("date.in=" + DEFAULT_DATE + "," + UPDATED_DATE, "date.in=" + UPDATED_DATE);
    }

    @Test
    void getAllAlbumsByDateIsNullOrNotNull() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date is not null
        defaultAlbumsFiltering("date.specified=true", "date.specified=false");
    }

    @Test
    void getAllAlbumsByDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date is greater than or equal to
        defaultAlbumsFiltering("date.greaterThanOrEqual=" + DEFAULT_DATE, "date.greaterThanOrEqual=" + UPDATED_DATE);
    }

    @Test
    void getAllAlbumsByDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date is less than or equal to
        defaultAlbumsFiltering("date.lessThanOrEqual=" + DEFAULT_DATE, "date.lessThanOrEqual=" + SMALLER_DATE);
    }

    @Test
    void getAllAlbumsByDateIsLessThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date is less than
        defaultAlbumsFiltering("date.lessThan=" + UPDATED_DATE, "date.lessThan=" + DEFAULT_DATE);
    }

    @Test
    void getAllAlbumsByDateIsGreaterThanSomething() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        // Get all the albumsList where date is greater than
        defaultAlbumsFiltering("date.greaterThan=" + SMALLER_DATE, "date.greaterThan=" + DEFAULT_DATE);
    }

    private void defaultAlbumsFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultAlbumsShouldBeFound(shouldBeFound);
        defaultAlbumsShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultAlbumsShouldBeFound(String filter) {
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
            .value(hasItem(albums.getId().intValue()))
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
            .jsonPath("$.[*].date")
            .value(hasItem(sameInstant(DEFAULT_DATE)));

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
    private void defaultAlbumsShouldNotBeFound(String filter) {
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
    void getNonExistingAlbums() {
        // Get the albums
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingAlbums() throws Exception {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the albums
        Albums updatedAlbums = albumsRepository.findById(albums.getId()).block();
        updatedAlbums
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .date(UPDATED_DATE);
        AlbumsDTO albumsDTO = albumsMapper.toDto(updatedAlbums);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, albumsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAlbumsToMatchAllProperties(updatedAlbums);
    }

    @Test
    void putNonExistingAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, albumsDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateAlbumsWithPatch() throws Exception {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the albums using partial update
        Albums partialUpdatedAlbums = new Albums();
        partialUpdatedAlbums.setId(albums.getId());

        partialUpdatedAlbums.insertBy(UPDATED_INSERT_BY);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedAlbums.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedAlbums))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Albums in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAlbumsUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedAlbums, albums), getPersistedAlbums(albums));
    }

    @Test
    void fullUpdateAlbumsWithPatch() throws Exception {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the albums using partial update
        Albums partialUpdatedAlbums = new Albums();
        partialUpdatedAlbums.setId(albums.getId());

        partialUpdatedAlbums
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .date(UPDATED_DATE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedAlbums.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedAlbums))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Albums in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAlbumsUpdatableFieldsEquals(partialUpdatedAlbums, getPersistedAlbums(partialUpdatedAlbums));
    }

    @Test
    void patchNonExistingAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, albumsDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamAlbums() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        albums.setId(longCount.incrementAndGet());

        // Create the Albums
        AlbumsDTO albumsDTO = albumsMapper.toDto(albums);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(albumsDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Albums in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteAlbums() {
        // Initialize the database
        insertedAlbums = albumsRepository.save(albums).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the albums
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, albums.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return albumsRepository.count().block();
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

    protected Albums getPersistedAlbums(Albums albums) {
        return albumsRepository.findById(albums.getId()).block();
    }

    protected void assertPersistedAlbumsToMatchAllProperties(Albums expectedAlbums) {
        // Test fails because reactive api returns an empty object instead of null
        // assertAlbumsAllPropertiesEquals(expectedAlbums, getPersistedAlbums(expectedAlbums));
        assertAlbumsUpdatableFieldsEquals(expectedAlbums, getPersistedAlbums(expectedAlbums));
    }

    protected void assertPersistedAlbumsToMatchUpdatableProperties(Albums expectedAlbums) {
        // Test fails because reactive api returns an empty object instead of null
        // assertAlbumsAllUpdatablePropertiesEquals(expectedAlbums, getPersistedAlbums(expectedAlbums));
        assertAlbumsUpdatableFieldsEquals(expectedAlbums, getPersistedAlbums(expectedAlbums));
    }
}
