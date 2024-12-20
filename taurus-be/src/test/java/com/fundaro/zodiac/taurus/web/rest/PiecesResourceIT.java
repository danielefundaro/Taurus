package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.PiecesAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.enumeration.PieceTypeEnum;
import com.fundaro.zodiac.taurus.repository.EntityManager;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.PiecesRepository;
import com.fundaro.zodiac.taurus.service.dto.PiecesDTO;
import com.fundaro.zodiac.taurus.service.mapper.PiecesMapper;
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
 * Integration tests for the {@link PiecesResource} REST controller.
 */
@IntegrationTest
@AutoConfigureWebTestClient(timeout = IntegrationTest.DEFAULT_ENTITY_TIMEOUT)
@WithMockUser
class PiecesResourceIT {

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

    private static final PieceTypeEnum DEFAULT_TYPE = PieceTypeEnum.IMAGE;
    private static final PieceTypeEnum UPDATED_TYPE = PieceTypeEnum.PDF;

    private static final String DEFAULT_CONTENT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_CONTENT_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_PATH = "AAAAAAAAAA";
    private static final String UPDATED_PATH = "BBBBBBBBBB";

    private static final Long DEFAULT_ORDER_NUMBER = 1L;
    private static final Long UPDATED_ORDER_NUMBER = 2L;
    private static final Long SMALLER_ORDER_NUMBER = 1L - 1L;

    private static final String ENTITY_API_URL = "/api/pieces";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PiecesRepository piecesRepository;

    @Autowired
    private PiecesMapper piecesMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private WebTestClient webTestClient;

    private Pieces pieces;

    private Pieces insertedPieces;

    @Autowired
    private MediaRepository mediaRepository;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Pieces createEntity(EntityManager em) {
        Pieces pieces = new Pieces()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .type(DEFAULT_TYPE)
            .contentType(DEFAULT_CONTENT_TYPE)
            .path(DEFAULT_PATH)
            .orderNumber(DEFAULT_ORDER_NUMBER);
        // Add required entity
        Media media;
        media = em.insert(MediaResourceIT.createEntity(em)).block();
        pieces.setMedia(media);
        return pieces;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Pieces createUpdatedEntity(EntityManager em) {
        Pieces updatedPieces = new Pieces()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .contentType(UPDATED_CONTENT_TYPE)
            .path(UPDATED_PATH)
            .orderNumber(UPDATED_ORDER_NUMBER);
        // Add required entity
        Media media;
        media = em.insert(MediaResourceIT.createUpdatedEntity(em)).block();
        updatedPieces.setMedia(media);
        return updatedPieces;
    }

    public static void deleteEntities(EntityManager em) {
        try {
            em.deleteAll(Pieces.class).block();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
        MediaResourceIT.deleteEntities(em);
    }

    @BeforeEach
    public void setupCsrf() {
        webTestClient = webTestClient.mutateWith(csrf());
    }

    @BeforeEach
    public void initTest() {
        pieces = createEntity(em);
    }

    @AfterEach
    public void cleanup() {
        if (insertedPieces != null) {
            piecesRepository.delete(insertedPieces).block();
            insertedPieces = null;
        }
        deleteEntities(em);
    }

    @Test
    void createPieces() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);
        var returnedPiecesDTO = webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(PiecesDTO.class)
            .returnResult()
            .getResponseBody();

        // Validate the Pieces in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPieces = piecesMapper.toEntity(returnedPiecesDTO);
        assertPiecesUpdatableFieldsEquals(returnedPieces, getPersistedPieces(returnedPieces));

        insertedPieces = returnedPieces;
    }

    @Test
    void createPiecesWithExistingId() throws Exception {
        // Create the Pieces with an existing ID
        pieces.setId(1L);
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pieces.setName(null);

        // Create the Pieces, which fails.
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pieces.setType(null);

        // Create the Pieces, which fails.
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkContentTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pieces.setContentType(null);

        // Create the Pieces, which fails.
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPathIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pieces.setPath(null);

        // Create the Pieces, which fails.
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        webTestClient
            .post()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPieces() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList
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
            .value(hasItem(pieces.getId().intValue()))
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
            .jsonPath("$.[*].type")
            .value(hasItem(DEFAULT_TYPE.toString()))
            .jsonPath("$.[*].contentType")
            .value(hasItem(DEFAULT_CONTENT_TYPE))
            .jsonPath("$.[*].path")
            .value(hasItem(DEFAULT_PATH))
            .jsonPath("$.[*].orderNumber")
            .value(hasItem(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getPieces() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get the pieces
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, pieces.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id")
            .value(is(pieces.getId().intValue()))
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
            .jsonPath("$.type")
            .value(is(DEFAULT_TYPE.toString()))
            .jsonPath("$.contentType")
            .value(is(DEFAULT_CONTENT_TYPE))
            .jsonPath("$.path")
            .value(is(DEFAULT_PATH))
            .jsonPath("$.orderNumber")
            .value(is(DEFAULT_ORDER_NUMBER.intValue()));
    }

    @Test
    void getPiecesByIdFiltering() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        Long id = pieces.getId();

        defaultPiecesFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultPiecesFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultPiecesFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllPiecesByDeletedIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where deleted equals to
        defaultPiecesFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllPiecesByDeletedIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where deleted in
        defaultPiecesFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllPiecesByDeletedIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where deleted is not null
        defaultPiecesFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllPiecesByInsertByIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertBy equals to
        defaultPiecesFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPiecesByInsertByIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertBy in
        defaultPiecesFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPiecesByInsertByIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertBy is not null
        defaultPiecesFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllPiecesByInsertByContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertBy contains
        defaultPiecesFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllPiecesByInsertByNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertBy does not contain
        defaultPiecesFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllPiecesByInsertDateIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate equals to
        defaultPiecesFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllPiecesByInsertDateIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate in
        defaultPiecesFiltering("insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE, "insertDate.in=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllPiecesByInsertDateIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate is not null
        defaultPiecesFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllPiecesByInsertDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate is greater than or equal to
        defaultPiecesFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllPiecesByInsertDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate is less than or equal to
        defaultPiecesFiltering("insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE, "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE);
    }

    @Test
    void getAllPiecesByInsertDateIsLessThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate is less than
        defaultPiecesFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPiecesByInsertDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where insertDate is greater than
        defaultPiecesFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllPiecesByEditByIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editBy equals to
        defaultPiecesFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPiecesByEditByIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editBy in
        defaultPiecesFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPiecesByEditByIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editBy is not null
        defaultPiecesFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllPiecesByEditByContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editBy contains
        defaultPiecesFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllPiecesByEditByNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editBy does not contain
        defaultPiecesFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllPiecesByEditDateIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate equals to
        defaultPiecesFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPiecesByEditDateIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate in
        defaultPiecesFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPiecesByEditDateIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate is not null
        defaultPiecesFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllPiecesByEditDateIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate is greater than or equal to
        defaultPiecesFiltering("editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllPiecesByEditDateIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate is less than or equal to
        defaultPiecesFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllPiecesByEditDateIsLessThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate is less than
        defaultPiecesFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPiecesByEditDateIsGreaterThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where editDate is greater than
        defaultPiecesFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllPiecesByNameIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where name equals to
        defaultPiecesFiltering("name.equals=" + DEFAULT_NAME, "name.equals=" + UPDATED_NAME);
    }

    @Test
    void getAllPiecesByNameIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where name in
        defaultPiecesFiltering("name.in=" + DEFAULT_NAME + "," + UPDATED_NAME, "name.in=" + UPDATED_NAME);
    }

    @Test
    void getAllPiecesByNameIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where name is not null
        defaultPiecesFiltering("name.specified=true", "name.specified=false");
    }

    @Test
    void getAllPiecesByNameContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where name contains
        defaultPiecesFiltering("name.contains=" + DEFAULT_NAME, "name.contains=" + UPDATED_NAME);
    }

    @Test
    void getAllPiecesByNameNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where name does not contain
        defaultPiecesFiltering("name.doesNotContain=" + UPDATED_NAME, "name.doesNotContain=" + DEFAULT_NAME);
    }

    @Test
    void getAllPiecesByDescriptionIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where description equals to
        defaultPiecesFiltering("description.equals=" + DEFAULT_DESCRIPTION, "description.equals=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllPiecesByDescriptionIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where description in
        defaultPiecesFiltering(
            "description.in=" + DEFAULT_DESCRIPTION + "," + UPDATED_DESCRIPTION,
            "description.in=" + UPDATED_DESCRIPTION
        );
    }

    @Test
    void getAllPiecesByDescriptionIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where description is not null
        defaultPiecesFiltering("description.specified=true", "description.specified=false");
    }

    @Test
    void getAllPiecesByDescriptionContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where description contains
        defaultPiecesFiltering("description.contains=" + DEFAULT_DESCRIPTION, "description.contains=" + UPDATED_DESCRIPTION);
    }

    @Test
    void getAllPiecesByDescriptionNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where description does not contain
        defaultPiecesFiltering("description.doesNotContain=" + UPDATED_DESCRIPTION, "description.doesNotContain=" + DEFAULT_DESCRIPTION);
    }

    @Test
    void getAllPiecesByTypeIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where type equals to
        defaultPiecesFiltering("type.equals=" + DEFAULT_TYPE, "type.equals=" + UPDATED_TYPE);
    }

    @Test
    void getAllPiecesByTypeIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where type in
        defaultPiecesFiltering("type.in=" + DEFAULT_TYPE + "," + UPDATED_TYPE, "type.in=" + UPDATED_TYPE);
    }

    @Test
    void getAllPiecesByTypeIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where type is not null
        defaultPiecesFiltering("type.specified=true", "type.specified=false");
    }

    @Test
    void getAllPiecesByContentTypeIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where contentType equals to
        defaultPiecesFiltering("contentType.equals=" + DEFAULT_CONTENT_TYPE, "contentType.equals=" + UPDATED_CONTENT_TYPE);
    }

    @Test
    void getAllPiecesByContentTypeIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where contentType in
        defaultPiecesFiltering(
            "contentType.in=" + DEFAULT_CONTENT_TYPE + "," + UPDATED_CONTENT_TYPE,
            "contentType.in=" + UPDATED_CONTENT_TYPE
        );
    }

    @Test
    void getAllPiecesByContentTypeIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where contentType is not null
        defaultPiecesFiltering("contentType.specified=true", "contentType.specified=false");
    }

    @Test
    void getAllPiecesByContentTypeContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where contentType contains
        defaultPiecesFiltering("contentType.contains=" + DEFAULT_CONTENT_TYPE, "contentType.contains=" + UPDATED_CONTENT_TYPE);
    }

    @Test
    void getAllPiecesByContentTypeNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where contentType does not contain
        defaultPiecesFiltering("contentType.doesNotContain=" + UPDATED_CONTENT_TYPE, "contentType.doesNotContain=" + DEFAULT_CONTENT_TYPE);
    }

    @Test
    void getAllPiecesByPathIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where path equals to
        defaultPiecesFiltering("path.equals=" + DEFAULT_PATH, "path.equals=" + UPDATED_PATH);
    }

    @Test
    void getAllPiecesByPathIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where path in
        defaultPiecesFiltering("path.in=" + DEFAULT_PATH + "," + UPDATED_PATH, "path.in=" + UPDATED_PATH);
    }

    @Test
    void getAllPiecesByPathIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where path is not null
        defaultPiecesFiltering("path.specified=true", "path.specified=false");
    }

    @Test
    void getAllPiecesByPathContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where path contains
        defaultPiecesFiltering("path.contains=" + DEFAULT_PATH, "path.contains=" + UPDATED_PATH);
    }

    @Test
    void getAllPiecesByPathNotContainsSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where path does not contain
        defaultPiecesFiltering("path.doesNotContain=" + UPDATED_PATH, "path.doesNotContain=" + DEFAULT_PATH);
    }

    @Test
    void getAllPiecesByOrderNumberIsEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber equals to
        defaultPiecesFiltering("orderNumber.equals=" + DEFAULT_ORDER_NUMBER, "orderNumber.equals=" + UPDATED_ORDER_NUMBER);
    }

    @Test
    void getAllPiecesByOrderNumberIsInShouldWork() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber in
        defaultPiecesFiltering(
            "orderNumber.in=" + DEFAULT_ORDER_NUMBER + "," + UPDATED_ORDER_NUMBER,
            "orderNumber.in=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllPiecesByOrderNumberIsNullOrNotNull() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber is not null
        defaultPiecesFiltering("orderNumber.specified=true", "orderNumber.specified=false");
    }

    @Test
    void getAllPiecesByOrderNumberIsGreaterThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber is greater than or equal to
        defaultPiecesFiltering(
            "orderNumber.greaterThanOrEqual=" + DEFAULT_ORDER_NUMBER,
            "orderNumber.greaterThanOrEqual=" + UPDATED_ORDER_NUMBER
        );
    }

    @Test
    void getAllPiecesByOrderNumberIsLessThanOrEqualToSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber is less than or equal to
        defaultPiecesFiltering(
            "orderNumber.lessThanOrEqual=" + DEFAULT_ORDER_NUMBER,
            "orderNumber.lessThanOrEqual=" + SMALLER_ORDER_NUMBER
        );
    }

    @Test
    void getAllPiecesByOrderNumberIsLessThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber is less than
        defaultPiecesFiltering("orderNumber.lessThan=" + UPDATED_ORDER_NUMBER, "orderNumber.lessThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllPiecesByOrderNumberIsGreaterThanSomething() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        // Get all the piecesList where orderNumber is greater than
        defaultPiecesFiltering("orderNumber.greaterThan=" + SMALLER_ORDER_NUMBER, "orderNumber.greaterThan=" + DEFAULT_ORDER_NUMBER);
    }

    @Test
    void getAllPiecesByMediaIsEqualToSomething() {
        Media media = MediaResourceIT.createEntity(em);
        mediaRepository.save(media).block();
        Long mediaId = media.getId();
        pieces.setMediaId(mediaId);
        insertedPieces = piecesRepository.save(pieces).block();
        // Get all the piecesList where media equals to mediaId
        defaultPiecesShouldBeFound("mediaId.equals=" + mediaId);

        // Get all the piecesList where media equals to (mediaId + 1)
        defaultPiecesShouldNotBeFound("mediaId.equals=" + (mediaId + 1));
    }

    private void defaultPiecesFiltering(String shouldBeFound, String shouldNotBeFound) {
        defaultPiecesShouldBeFound(shouldBeFound);
        defaultPiecesShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultPiecesShouldBeFound(String filter) {
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
            .value(hasItem(pieces.getId().intValue()))
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
            .jsonPath("$.[*].type")
            .value(hasItem(DEFAULT_TYPE.toString()))
            .jsonPath("$.[*].contentType")
            .value(hasItem(DEFAULT_CONTENT_TYPE))
            .jsonPath("$.[*].path")
            .value(hasItem(DEFAULT_PATH))
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
    private void defaultPiecesShouldNotBeFound(String filter) {
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
    void getNonExistingPieces() {
        // Get the pieces
        webTestClient
            .get()
            .uri(ENTITY_API_URL_ID, Long.MAX_VALUE)
            .accept(MediaType.APPLICATION_PROBLEM_JSON)
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    void putExistingPieces() throws Exception {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pieces
        Pieces updatedPieces = piecesRepository.findById(pieces.getId()).block();
        updatedPieces
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .contentType(UPDATED_CONTENT_TYPE)
            .path(UPDATED_PATH)
            .orderNumber(UPDATED_ORDER_NUMBER);
        PiecesDTO piecesDTO = piecesMapper.toDto(updatedPieces);

        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, piecesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPiecesToMatchAllProperties(updatedPieces);
    }

    @Test
    void putNonExistingPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, piecesDTO.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .put()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePiecesWithPatch() throws Exception {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pieces using partial update
        Pieces partialUpdatedPieces = new Pieces();
        partialUpdatedPieces.setId(pieces.getId());

        partialUpdatedPieces
            .insertDate(UPDATED_INSERT_DATE)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .contentType(UPDATED_CONTENT_TYPE);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPieces.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPieces))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Pieces in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPiecesUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedPieces, pieces), getPersistedPieces(pieces));
    }

    @Test
    void fullUpdatePiecesWithPatch() throws Exception {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pieces using partial update
        Pieces partialUpdatedPieces = new Pieces();
        partialUpdatedPieces.setId(pieces.getId());

        partialUpdatedPieces
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .contentType(UPDATED_CONTENT_TYPE)
            .path(UPDATED_PATH)
            .orderNumber(UPDATED_ORDER_NUMBER);

        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, partialUpdatedPieces.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(partialUpdatedPieces))
            .exchange()
            .expectStatus()
            .isOk();

        // Validate the Pieces in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPiecesUpdatableFieldsEquals(partialUpdatedPieces, getPersistedPieces(partialUpdatedPieces));
    }

    @Test
    void patchNonExistingPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, piecesDTO.getId())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL_ID, longCount.incrementAndGet())
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isBadRequest();

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPieces() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pieces.setId(longCount.incrementAndGet());

        // Create the Pieces
        PiecesDTO piecesDTO = piecesMapper.toDto(pieces);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        webTestClient
            .patch()
            .uri(ENTITY_API_URL)
            .contentType(MediaType.valueOf("application/merge-patch+json"))
            .bodyValue(om.writeValueAsBytes(piecesDTO))
            .exchange()
            .expectStatus()
            .isEqualTo(405);

        // Validate the Pieces in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePieces() {
        // Initialize the database
        insertedPieces = piecesRepository.save(pieces).block();

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the pieces
        webTestClient
            .delete()
            .uri(ENTITY_API_URL_ID, pieces.getId())
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .isNoContent();

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return piecesRepository.count().block();
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

    protected Pieces getPersistedPieces(Pieces pieces) {
        return piecesRepository.findById(pieces.getId()).block();
    }

    protected void assertPersistedPiecesToMatchAllProperties(Pieces expectedPieces) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPiecesAllPropertiesEquals(expectedPieces, getPersistedPieces(expectedPieces));
        assertPiecesUpdatableFieldsEquals(expectedPieces, getPersistedPieces(expectedPieces));
    }

    protected void assertPersistedPiecesToMatchUpdatableProperties(Pieces expectedPieces) {
        // Test fails because reactive api returns an empty object instead of null
        // assertPiecesAllUpdatablePropertiesEquals(expectedPieces, getPersistedPieces(expectedPieces));
        assertPiecesUpdatableFieldsEquals(expectedPieces, getPersistedPieces(expectedPieces));
    }
}
