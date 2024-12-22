package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.LkTrackTypeAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.LkTrackTypeRepository;
import com.fundaro.zodiac.taurus.service.dto.LkTrackTypeDTO;
import com.fundaro.zodiac.taurus.service.mapper.LkTrackTypeMapper;
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
 * Integration tests for the {@link LkTrackTypeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class LkTrackTypeResourceIT {

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

    private static final String ENTITY_API_URL = "/api/lk-track-types";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private LkTrackTypeRepository lkTrackTypeRepository;

    @Autowired
    private LkTrackTypeMapper lkTrackTypeMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private LkTrackType lkTrackType;

    private LkTrackType insertedLkTrackType;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LkTrackType createEntity() {
        return new LkTrackType()
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
    public static LkTrackType createUpdatedEntity() {
        return new LkTrackType()
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
            em.deleteAll(LkTrackType.class).block();
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
        lkTrackType = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedLkTrackType != null) {
            lkTrackTypeRepository.delete(insertedLkTrackType).block();
            insertedLkTrackType = null;
        }
        deleteEntities(em);
    }

    @Test
    void createLkTrackType() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);
        var returnedLkTrackTypeDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(LkTrackTypeDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the LkTrackType in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedLkTrackType = lkTrackTypeMapper.toEntity(returnedLkTrackTypeDTO);
        assertLkTrackTypeUpdatableFieldsEquals(returnedLkTrackType, getPersistedLkTrackType(returnedLkTrackType));

        insertedLkTrackType = returnedLkTrackType;
    }

    @Test
    void createLkTrackTypeWithExistingId() throws Exception {
        // Create the LkTrackType with an existing ID
        lkTrackType.setId(1L);
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        lkTrackType.setName(null);

        // Create the LkTrackType, which fails.
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllLkTrackTypes() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList
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
            .value(hasItem(lkTrackType.getId().intValue()))
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
    void getLkTrackType() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get the lkTrackType
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, lkTrackType.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(lkTrackType.getId().intValue()))
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
    void getLkTrackTypesByIdFiltering() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        Long id = lkTrackType.getId();

        defaultLkTrackTypeFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultLkTrackTypeFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultLkTrackTypeFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllLkTrackTypesByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where deleted equals to
        defaultLkTrackTypeFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllLkTrackTypesByDeletedIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where deleted in
        defaultLkTrackTypeFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllLkTrackTypesByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where deleted is not null
        defaultLkTrackTypeFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllLkTrackTypesByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertBy equals to
        defaultLkTrackTypeFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLkTrackTypesByInsertByIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertBy in
        defaultLkTrackTypeFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLkTrackTypesByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertBy is not null
        defaultLkTrackTypeFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllLkTrackTypesByInsertByContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertBy contains
        defaultLkTrackTypeFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLkTrackTypesByInsertByNotContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertBy does not contain
        defaultLkTrackTypeFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate equals to
        defaultLkTrackTypeFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate in
        defaultLkTrackTypeFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate is not null
        defaultLkTrackTypeFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate is greater than or equal to
        defaultLkTrackTypeFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate is less than or equal to
        defaultLkTrackTypeFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate is less than
        defaultLkTrackTypeFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllLkTrackTypesByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where insertDate is greater than
        defaultLkTrackTypeFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditByIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editBy equals to
        defaultLkTrackTypeFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLkTrackTypesByEditByIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editBy in
        defaultLkTrackTypeFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLkTrackTypesByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editBy is not null
        defaultLkTrackTypeFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllLkTrackTypesByEditByContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editBy contains
        defaultLkTrackTypeFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLkTrackTypesByEditByNotContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editBy does not contain
        defaultLkTrackTypeFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate equals to
        defaultLkTrackTypeFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate in
        defaultLkTrackTypeFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate is not null
        defaultLkTrackTypeFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllLkTrackTypesByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate is greater than or equal to
        defaultLkTrackTypeFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate is less than or equal to
        defaultLkTrackTypeFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate is less than
        defaultLkTrackTypeFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where editDate is greater than
        defaultLkTrackTypeFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllLkTrackTypesByNameIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where name equals to
        defaultLkTrackTypeFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllLkTrackTypesByNameIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where name in
        defaultLkTrackTypeFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllLkTrackTypesByNameIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where name is not null
        defaultLkTrackTypeFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllLkTrackTypesByNameContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where name contains
        defaultLkTrackTypeFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllLkTrackTypesByNameNotContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where name does not contain
        defaultLkTrackTypeFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllLkTrackTypesByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where description equals to
        defaultLkTrackTypeFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllLkTrackTypesByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where description in
        defaultLkTrackTypeFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllLkTrackTypesByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where description is not null
        defaultLkTrackTypeFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllLkTrackTypesByDescriptionContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where description contains
        defaultLkTrackTypeFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllLkTrackTypesByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        // Get all the lkTrackTypeList where description does not contain
        defaultLkTrackTypeFiltering(
            "description.doesNotContain=" + UPDATED_DESCRIPTION,
            "description.doesNotContain=" + DEFAULT_DESCRIPTION
        );
    }

    private void defaultLkTrackTypeFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultLkTrackTypeShouldBeFound(shouldBeFound);
        defaultLkTrackTypeShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultLkTrackTypeShouldBeFound(String filter) {
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
            .value(hasItem(lkTrackType.getId().intValue()))
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
    private void defaultLkTrackTypeShouldNotBeFound(String filter) {
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
    void getNonExistingLkTrackType() {
        // Get the lkTrackType
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingLkTrackType() throws Exception {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lkTrackType
        LkTrackType updatedLkTrackType = lkTrackTypeRepository.findById(lkTrackType.getId()).block();
        updatedLkTrackType
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION);
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(updatedLkTrackType);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, lkTrackTypeDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedLkTrackTypeToMatchAllProperties(updatedLkTrackType);
    }

    @Test
    void putNonExistingLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, lkTrackTypeDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateLkTrackTypeWithPatch() throws Exception {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lkTrackType using partial update
        LkTrackType partialUpdatedLkTrackType = new LkTrackType();
        partialUpdatedLkTrackType.setId(lkTrackType.getId());

        partialUpdatedLkTrackType.insertBy(UPDATED_INSERT_BY).editBy(UPDATED_EDIT_BY).editDate(UPDATED_EDIT_DATE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedLkTrackType.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedLkTrackType))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the LkTrackType in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLkTrackTypeUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedLkTrackType, lkTrackType),
            getPersistedLkTrackType(lkTrackType)
        );
    }

    @Test
    void fullUpdateLkTrackTypeWithPatch() throws Exception {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lkTrackType using partial update
        LkTrackType partialUpdatedLkTrackType = new LkTrackType();
        partialUpdatedLkTrackType.setId(lkTrackType.getId());

        partialUpdatedLkTrackType
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedLkTrackType.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedLkTrackType))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the LkTrackType in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLkTrackTypeUpdatableFieldsEquals(partialUpdatedLkTrackType, getPersistedLkTrackType(partialUpdatedLkTrackType));
    }

    @Test
    void patchNonExistingLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, lkTrackTypeDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamLkTrackType() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lkTrackType.setId(longCount.incrementAndGet());

        // Create the LkTrackType
        LkTrackTypeDTO lkTrackTypeDTO = lkTrackTypeMapper.toDto(lkTrackType);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(lkTrackTypeDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the LkTrackType in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteLkTrackType() {
        // Initialize the database
        insertedLkTrackType = lkTrackTypeRepository.save(lkTrackType).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the lkTrackType
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, lkTrackType.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return lkTrackTypeRepository.count().block();
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

    protected LkTrackType getPersistedLkTrackType(LkTrackType lkTrackType) {
        return lkTrackTypeRepository.findById(lkTrackType.getId()).block();
    }

    protected void assertPersistedLkTrackTypeToMatchAllProperties(LkTrackType expectedLkTrackType) {
        // Test fails because reactive api returns an empty object instead of null
        // assertLkTrackTypeAllPropertiesEquals(expectedLkTrackType, getPersistedLkTrackType(expectedLkTrackType));
        assertLkTrackTypeUpdatableFieldsEquals(expectedLkTrackType, getPersistedLkTrackType(expectedLkTrackType));
    }

    protected void assertPersistedLkTrackTypeToMatchUpdatableProperties(LkTrackType expectedLkTrackType) {
        // Test fails because reactive api returns an empty object instead of null
        // assertLkTrackTypeAllUpdatablePropertiesEquals(expectedLkTrackType, getPersistedLkTrackType(expectedLkTrackType));
        assertLkTrackTypeUpdatableFieldsEquals(expectedLkTrackType, getPersistedLkTrackType(expectedLkTrackType));
    }
}
