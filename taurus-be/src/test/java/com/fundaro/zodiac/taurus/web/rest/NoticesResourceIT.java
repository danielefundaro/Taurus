package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.NoticesAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
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
 * Integration tests for the {@link NoticesResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class NoticesResourceIT {

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

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_MESSAGE = "AAAAAAAAAA";
    private static final String UPDATED_MESSAGE = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_READ_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_READ_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);
    private static final ZonedDateTime SMALLER_READ_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(-1L), ZoneOffset.UTC);

    private static final String ENTITY_API_URL = "/api/notices";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private NoticesRepository noticesRepository;

    @Autowired
    private NoticesMapper noticesMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Notices notices;

    private Notices insertedNotices;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Notices createEntity() {
        return new Notices()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .userId(DEFAULT_USER_ID)
            .name(DEFAULT_NAME)
            .message(DEFAULT_MESSAGE)
            .readDate(DEFAULT_READ_DATE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Notices createUpdatedEntity() {
        return new Notices()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .name(UPDATED_NAME)
            .message(UPDATED_MESSAGE)
            .readDate(UPDATED_READ_DATE);
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Notices.class).block();
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
        notices = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedNotices != null) {
            noticesRepository.delete(insertedNotices).block();
            insertedNotices = null;
        }
        deleteEntities(em);
    }

    @Test
    void createNotices() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);
        var returnedNoticesDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(NoticesDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Notices in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedNotices = noticesMapper.toEntity(returnedNoticesDTO);
        assertNoticesUpdatableFieldsEquals(returnedNotices, getPersistedNotices(returnedNotices));

        insertedNotices = returnedNotices;
    }

    @Test
    void createNoticesWithExistingId() throws Exception {
        // Create the Notices with an existing ID
        notices.setId(1L);
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkUserIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        notices.setUserId(null);

        // Create the Notices, which fails.
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        notices.setName(null);

        // Create the Notices, which fails.
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllNotices() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList
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
            .value(hasItem(notices.getId().intValue()))
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
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME))
            .jsonPath("$.[*].message")
            .value(hasItem(DEFAULT_MESSAGE))
            .jsonPath("$.[*].readDate")
            .value(hasItem(sameInstant(DEFAULT_READ_DATE)));
    }

    @Test
    void getNotices() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get the notices
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, notices.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(notices.getId().intValue()))
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
            .jsonPath("$.name")
            .value(is(DEFAULT_NAME))
            .jsonPath("$.message")
            .value(is(DEFAULT_MESSAGE))
            .jsonPath("$.readDate")
            .value(is(sameInstant(DEFAULT_READ_DATE)));
    }

    @Test
    void getNoticesByIdFiltering() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        Long id = notices.getId();

        defaultNoticesFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultNoticesFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultNoticesFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllNoticesByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where deleted equals to
        defaultNoticesFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllNoticesByDeletedIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where deleted in
        defaultNoticesFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllNoticesByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where deleted is not null
        defaultNoticesFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllNoticesByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertBy equals to
        defaultNoticesFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllNoticesByInsertByIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertBy in
        defaultNoticesFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllNoticesByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertBy is not null
        defaultNoticesFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllNoticesByInsertByContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertBy contains
        defaultNoticesFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllNoticesByInsertByNotContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertBy does not contain
        defaultNoticesFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllNoticesByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate equals to
        defaultNoticesFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllNoticesByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate in
        defaultNoticesFiltering("insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE, "insertDate.in=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllNoticesByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate is not null
        defaultNoticesFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllNoticesByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate is greater than or equal to
        defaultNoticesFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllNoticesByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate is less than or equal to
        defaultNoticesFiltering("insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE, "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE);
    }

    @Test
    void getAllNoticesByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate is less than
        defaultNoticesFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllNoticesByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where insertDate is greater than
        defaultNoticesFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllNoticesByEditByIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editBy equals to
        defaultNoticesFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllNoticesByEditByIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editBy in
        defaultNoticesFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllNoticesByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editBy is not null
        defaultNoticesFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllNoticesByEditByContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editBy contains
        defaultNoticesFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllNoticesByEditByNotContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editBy does not contain
        defaultNoticesFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllNoticesByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate equals to
        defaultNoticesFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllNoticesByEditDateIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate in
        defaultNoticesFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllNoticesByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate is not null
        defaultNoticesFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllNoticesByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate is greater than or equal to
        defaultNoticesFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllNoticesByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate is less than or equal to
        defaultNoticesFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllNoticesByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate is less than
        defaultNoticesFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllNoticesByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where editDate is greater than
        defaultNoticesFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllNoticesByUserIdIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where userId equals to
        defaultNoticesFiltering("userId.equals=" + DEFAULT_USER_ID, "userId.equals=" + UPDATED_USER_ID);
    }

    @Test
    void getAllNoticesByUserIdIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where userId in
        defaultNoticesFiltering("userId.in=" + DEFAULT_USER_ID + "," + UPDATED_USER_ID, "userId.in=" + UPDATED_USER_ID);
    }

    @Test
    void getAllNoticesByUserIdIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where userId is not null
        defaultNoticesFiltering("userId.specified=true", "userId.specified=false");
    }

    @Test
    void getAllNoticesByUserIdContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where userId contains
        defaultNoticesFiltering("userId.contains=" + DEFAULT_USER_ID, "userId.contains=" + UPDATED_USER_ID);
    }

    @Test
    void getAllNoticesByUserIdNotContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where userId does not contain
        defaultNoticesFiltering("userId.doesNotContain=" + UPDATED_USER_ID, "userId.doesNotContain=" + DEFAULT_USER_ID);
    }

    @Test
    void getAllNoticesByNameIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where name equals to
        defaultNoticesFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllNoticesByNameIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where name in
        defaultNoticesFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllNoticesByNameIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where name is not null
        defaultNoticesFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllNoticesByNameContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where name contains
        defaultNoticesFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllNoticesByNameNotContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where name does not contain
        defaultNoticesFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllNoticesByMessageIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where message equals to
        defaultNoticesFiltering("message.equals=" + DEFAULT_MESSAGE, "message.equals=" + UPDATED_MESSAGE);
    }

    @Test
    void getAllNoticesByMessageIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where message in
        defaultNoticesFiltering("message.in=" + DEFAULT_MESSAGE + "," + UPDATED_MESSAGE, "message.in=" + UPDATED_MESSAGE);
    }

    @Test
    void getAllNoticesByMessageIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where message is not null
        defaultNoticesFiltering("message.specified=true", "message.specified=false");
    }

    @Test
    void getAllNoticesByMessageContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where message contains
        defaultNoticesFiltering("message.contains=" + DEFAULT_MESSAGE, "message.contains=" + UPDATED_MESSAGE);
    }

    @Test
    void getAllNoticesByMessageNotContainsSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where message does not contain
        defaultNoticesFiltering("message.doesNotContain=" + UPDATED_MESSAGE, "message.doesNotContain=" + DEFAULT_MESSAGE);
    }

    @Test
    void getAllNoticesByReadDateIsEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate equals to
        defaultNoticesFiltering("readDate.equals=" + DEFAULT_READ_DATE, "readDate.equals=" + UPDATED_READ_DATE);
    }

    @Test
    void getAllNoticesByReadDateIsInShouldWork() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate in
        defaultNoticesFiltering("readDate.in=" + DEFAULT_READ_DATE + "," + UPDATED_READ_DATE, "readDate.in=" + UPDATED_READ_DATE);
    }

    @Test
    void getAllNoticesByReadDateIsNullOrNotNull() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate is not null
        defaultNoticesFiltering("readDate.specified=true", "readDate.specified=false");
    }

    @Test
    void getAllNoticesByReadDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate is greater than or equal to
        defaultNoticesFiltering("readDate.greaterThanOrEqual=" + DEFAULT_READ_DATE, "readDate.greaterThanOrEqual=" + UPDATED_READ_DATE);
    }

    @Test
    void getAllNoticesByReadDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate is less than or equal to
        defaultNoticesFiltering("readDate.lessThanOrEqual=" + DEFAULT_READ_DATE, "readDate.lessThanOrEqual=" + SMALLER_READ_DATE);
    }

    @Test
    void getAllNoticesByReadDateIsLessThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate is less than
        defaultNoticesFiltering("readDate.lessThan=" + UPDATED_READ_DATE, "readDate.lessThan=" + DEFAULT_READ_DATE);
    }

    @Test
    void getAllNoticesByReadDateIsGreaterThanSomething() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        // Get all the noticesList where readDate is greater than
        defaultNoticesFiltering("readDate.greaterThan=" + SMALLER_READ_DATE, "readDate.greaterThan=" + DEFAULT_READ_DATE);
    }

    private void defaultNoticesFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultNoticesShouldBeFound(shouldBeFound);
        defaultNoticesShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultNoticesShouldBeFound(String filter) {
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
            .value(hasItem(notices.getId().intValue()))
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
            .jsonPath("$.[*].name")
            .value(hasItem(DEFAULT_NAME))
            .jsonPath("$.[*].message")
            .value(hasItem(DEFAULT_MESSAGE))
            .jsonPath("$.[*].readDate")
            .value(hasItem(sameInstant(DEFAULT_READ_DATE)));

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
    private void defaultNoticesShouldNotBeFound(String filter) {
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
    void getNonExistingNotices() {
        // Get the notices
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingNotices() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the notices
        Notices updatedNotices = noticesRepository.findByIdAndUserId(notices.getId(), "").block();
        updatedNotices
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .name(UPDATED_NAME)
            .message(UPDATED_MESSAGE)
            .readDate(UPDATED_READ_DATE);
        NoticesDTO noticesDTO = noticesMapper.toDto(updatedNotices);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, noticesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedNoticesToMatchAllProperties(updatedNotices);
    }

    @Test
    void putNonExistingNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, noticesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateNoticesWithPatch() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the notices using partial update
        Notices partialUpdatedNotices = new Notices();
        partialUpdatedNotices.setId(notices.getId());

        partialUpdatedNotices.editBy(UPDATED_EDIT_BY).message(UPDATED_MESSAGE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedNotices.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedNotices))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Notices in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertNoticesUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedNotices, notices), getPersistedNotices(notices));
    }

    @Test
    void fullUpdateNoticesWithPatch() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the notices using partial update
        Notices partialUpdatedNotices = new Notices();
        partialUpdatedNotices.setId(notices.getId());

        partialUpdatedNotices
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .name(UPDATED_NAME)
            .message(UPDATED_MESSAGE)
            .readDate(UPDATED_READ_DATE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedNotices.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedNotices))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Notices in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertNoticesUpdatableFieldsEquals(partialUpdatedNotices, getPersistedNotices(partialUpdatedNotices));
    }

    @Test
    void patchNonExistingNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, noticesDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(noticesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteNotices() {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the notices
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, notices.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return noticesRepository.count().block();
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

    protected Notices getPersistedNotices(Notices notices) {
        return noticesRepository.findByIdAndUserId(notices.getId(), "").block();
    }

    protected void assertPersistedNoticesToMatchAllProperties(Notices expectedNotices) {
        // Test fails because reactive api returns an empty object instead of null
        // assertNoticesAllPropertiesEquals(expectedNotices, getPersistedNotices(expectedNotices));
        assertNoticesUpdatableFieldsEquals(expectedNotices, getPersistedNotices(expectedNotices));
    }

    protected void assertPersistedNoticesToMatchUpdatableProperties(Notices expectedNotices) {
        // Test fails because reactive api returns an empty object instead of null
        // assertNoticesAllUpdatablePropertiesEquals(expectedNotices, getPersistedNotices(expectedNotices));
        assertNoticesUpdatableFieldsEquals(expectedNotices, getPersistedNotices(expectedNotices));
    }
}
