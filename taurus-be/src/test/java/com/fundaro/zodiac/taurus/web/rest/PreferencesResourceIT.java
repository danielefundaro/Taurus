package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.PreferencesAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import com.fundaro.zodiac.taurus.service.mapper.PreferencesMapper;
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
 * Integration tests for the {@link PreferencesResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class PreferencesResourceIT {

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

    private static final String DEFAULT_USER_ID = "AAAAAAAAAA";
    private static final String UPDATED_USER_ID = "BBBBBBBBBB";

    private static final String DEFAULT_KEY = "AAAAAAAAAA";
    private static final String UPDATED_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_VALUE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/preferences";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PreferencesRepository preferencesRepository;

    @Autowired
    private PreferencesMapper preferencesMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Preferences preferences;

    private Preferences insertedPreferences;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Preferences createEntity() {
        return new Preferences()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .userId(DEFAULT_USER_ID)
            .key(DEFAULT_KEY)
            .value(DEFAULT_VALUE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Preferences createUpdatedEntity() {
        return new Preferences()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .key(UPDATED_KEY)
            .value(UPDATED_VALUE);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Preferences.class).block();
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
        preferences = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedPreferences != null) {
            preferencesRepository.delete(insertedPreferences).block();
            insertedPreferences = null;
        }
        deleteEntities(em);
    }

    @Test
    void createPreferences() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);
        var returnedPreferencesDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(PreferencesDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Preferences in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPreferences = preferencesMapper.toEntity(returnedPreferencesDTO);
        assertPreferencesUpdatableFieldsEquals(returnedPreferences, getPersistedPreferences(returnedPreferences));

        insertedPreferences = returnedPreferences;
    }

    @Test
    void createPreferencesWithExistingId() throws Exception {
        // Create the Preferences with an existing ID
        preferences.setId(1L);
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkUserIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        preferences.setUserId(null);

        // Create the Preferences, which fails.
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkKeyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        preferences.setKey(null);

        // Create the Preferences, which fails.
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPreferences() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList
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
            .value(hasItem(preferences.getId().intValue()))
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
            .jsonPath("$.[*].userId")
            .value(hasItem(DEFAULT_USER_ID))
            .jsonPath("$.[*].key")
            .value(hasItem(DEFAULT_KEY))
            .jsonPath("$.[*].value")
            .value(hasItem(DEFAULT_VALUE));
    }

    @Test
    void getPreferences() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get the preferences
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, preferences.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(preferences.getId().intValue()))
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
            .jsonPath("$.userId")
            .value(is(DEFAULT_USER_ID))
            .jsonPath("$.key")
            .value(is(DEFAULT_KEY))
            .jsonPath("$.value")
            .value(is(DEFAULT_VALUE));
    }

    @Test
    void getPreferencesByIdFiltering() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        Long id = preferences.getId();

        defaultPreferencesFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultPreferencesFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultPreferencesFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllPreferencesByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where deleted equals to
        defaultPreferencesFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllPreferencesByDeletedIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where deleted in
        defaultPreferencesFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllPreferencesByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where deleted is not null
        defaultPreferencesFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllPreferencesByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertBy equals to
        defaultPreferencesFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPreferencesByInsertByIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertBy in
        defaultPreferencesFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPreferencesByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertBy is not null
        defaultPreferencesFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllPreferencesByInsertByContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertBy contains
        defaultPreferencesFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPreferencesByInsertByNotContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertBy does not contain
        defaultPreferencesFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllPreferencesByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate equals to
        defaultPreferencesFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllPreferencesByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate in
        defaultPreferencesFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllPreferencesByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate is not null
        defaultPreferencesFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllPreferencesByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate is greater than or equal to
        defaultPreferencesFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllPreferencesByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate is less than or equal to
        defaultPreferencesFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllPreferencesByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate is less than
        defaultPreferencesFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPreferencesByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where insertDate is greater than
        defaultPreferencesFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPreferencesByEditByIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editBy equals to
        defaultPreferencesFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPreferencesByEditByIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editBy in
        defaultPreferencesFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPreferencesByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editBy is not null
        defaultPreferencesFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllPreferencesByEditByContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editBy contains
        defaultPreferencesFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPreferencesByEditByNotContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editBy does not contain
        defaultPreferencesFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllPreferencesByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate equals to
        defaultPreferencesFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByEditDateIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate in
        defaultPreferencesFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate is not null
        defaultPreferencesFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllPreferencesByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate is greater than or equal to
        defaultPreferencesFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate is less than or equal to
        defaultPreferencesFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate is less than
        defaultPreferencesFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where editDate is greater than
        defaultPreferencesFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPreferencesByUserIdIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where userId equals to
        defaultPreferencesFiltering("userId.equals=" + DEFAULT_USER_ID, "userId.equals=" + UPDATED_USER_ID);
    }

    @Test
    void getAllPreferencesByUserIdIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where userId in
        defaultPreferencesFiltering("userId.in=" + DEFAULT_USER_ID + "," + UPDATED_USER_ID, "userId.in=" + UPDATED_USER_ID);
    }

    @Test
    void getAllPreferencesByUserIdIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where userId is not null
        defaultPreferencesFiltering("userId.specified=true", "userId.specified=false");
    }

    @Test
    void getAllPreferencesByUserIdContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where userId contains
        defaultPreferencesFiltering("userId.contains=" + DEFAULT_USER_ID, "userId.contains=" + UPDATED_USER_ID);
    }

    @Test
    void getAllPreferencesByUserIdNotContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where userId does not contain
        defaultPreferencesFiltering("userId.doesNotContain=" + UPDATED_USER_ID, "userId.doesNotContain=" + DEFAULT_USER_ID);
    }

    @Test
    void getAllPreferencesByKeyIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where key equals to
        defaultPreferencesFiltering("key.equals=" + DEFAULT_KEY, "key.equals=" + UPDATED_KEY);
    }

    @Test
    void getAllPreferencesByKeyIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where key in
        defaultPreferencesFiltering("key.in=" + DEFAULT_KEY + "," + UPDATED_KEY, "key.in=" + UPDATED_KEY);
    }

    @Test
    void getAllPreferencesByKeyIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where key is not null
        defaultPreferencesFiltering("key.specified=true", "key.specified=false");
    }

    @Test
    void getAllPreferencesByKeyContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where key contains
        defaultPreferencesFiltering("key.contains=" + DEFAULT_KEY, "key.contains=" + UPDATED_KEY);
    }

    @Test
    void getAllPreferencesByKeyNotContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where key does not contain
        defaultPreferencesFiltering("key.doesNotContain=" + UPDATED_KEY, "key.doesNotContain=" + DEFAULT_KEY);
    }

    @Test
    void getAllPreferencesByValueIsEqualToSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where value equals to
        defaultPreferencesFiltering("value.equals=" + DEFAULT_VALUE, "value.equals=" + UPDATED_VALUE);
    }

    @Test
    void getAllPreferencesByValueIsInShouldWork() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where value in
        defaultPreferencesFiltering("value.in=" + DEFAULT_VALUE + "," + UPDATED_VALUE, "value.in=" + UPDATED_VALUE);
    }

    @Test
    void getAllPreferencesByValueIsNullOrNotNull() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where value is not null
        defaultPreferencesFiltering("value.specified=true", "value.specified=false");
    }

    @Test
    void getAllPreferencesByValueContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where value contains
        defaultPreferencesFiltering("value.contains=" + DEFAULT_VALUE, "value.contains=" + UPDATED_VALUE);
    }

    @Test
    void getAllPreferencesByValueNotContainsSomething() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        // Get all the preferencesList where value does not contain
        defaultPreferencesFiltering("value.doesNotContain=" + UPDATED_VALUE, "value.doesNotContain=" + DEFAULT_VALUE);
    }

    private void defaultPreferencesFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultPreferencesShouldBeFound(shouldBeFound);
        defaultPreferencesShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultPreferencesShouldBeFound(String filter) {
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
            .value(hasItem(preferences.getId().intValue()))
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
            .jsonPath("$.[*].userId")
            .value(hasItem(DEFAULT_USER_ID))
            .jsonPath("$.[*].key")
            .value(hasItem(DEFAULT_KEY))
            .jsonPath("$.[*].value")
            .value(hasItem(DEFAULT_VALUE));

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
    private void defaultPreferencesShouldNotBeFound(String filter) {
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
    void getNonExistingPreferences() {
        // Get the preferences
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingPreferences() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the preferences
        Preferences updatedPreferences = preferencesRepository.findByIdAndUserId(preferences.getId(), "").block();
        updatedPreferences
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .key(UPDATED_KEY)
            .value(UPDATED_VALUE);
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(updatedPreferences);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, preferencesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPreferencesToMatchAllProperties(updatedPreferences);
    }

    @Test
    void putNonExistingPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, preferencesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePreferencesWithPatch() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the preferences using partial update
        Preferences partialUpdatedPreferences = new Preferences();
        partialUpdatedPreferences.setId(preferences.getId());

        partialUpdatedPreferences.editDate(UPDATED_EDIT_DATE).userId(UPDATED_USER_ID);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPreferences.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPreferences))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Preferences in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPreferencesUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedPreferences, preferences),
            getPersistedPreferences(preferences)
        );
    }

    @Test
    void fullUpdatePreferencesWithPatch() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the preferences using partial update
        Preferences partialUpdatedPreferences = new Preferences();
        partialUpdatedPreferences.setId(preferences.getId());

        partialUpdatedPreferences
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .key(UPDATED_KEY)
            .value(UPDATED_VALUE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPreferences.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPreferences))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Preferences in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPreferencesUpdatableFieldsEquals(partialUpdatedPreferences, getPersistedPreferences(partialUpdatedPreferences));
    }

    @Test
    void patchNonExistingPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, preferencesDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(preferencesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePreferences() {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the preferences
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, preferences.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return preferencesRepository.count().block();
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

    protected Preferences getPersistedPreferences(Preferences preferences) {
        return preferencesRepository.findByIdAndUserId(preferences.getId(), "").block();
    }

    protected void assertPersistedPreferencesToMatchAllProperties(Preferences expectedPreferences) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPreferencesAllPropertiesEquals(expectedPreferences, getPersistedPreferences(expectedPreferences));
        assertPreferencesUpdatableFieldsEquals(expectedPreferences, getPersistedPreferences(expectedPreferences));
    }

    protected void assertPersistedPreferencesToMatchUpdatableProperties(Preferences expectedPreferences) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPreferencesAllUpdatablePropertiesEquals(expectedPreferences, getPersistedPreferences(expectedPreferences));
        assertPreferencesUpdatableFieldsEquals(expectedPreferences, getPersistedPreferences(expectedPreferences));
    }
}
