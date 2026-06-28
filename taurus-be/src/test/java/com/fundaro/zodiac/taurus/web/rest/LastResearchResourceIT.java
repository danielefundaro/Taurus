package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.domain.LastResearchAsserts.*;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.createUpdateProxyForBean;
import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.service.dto.LastResearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.LastResearchMapper;
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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration tests for the {@link LastResearchResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class LastResearchResourceIT {

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

    private static final String DEFAULT_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_VALUE = "BBBBBBBBBB";

    private static final String DEFAULT_FIELD = "AAAAAAAAAA";
    private static final String UPDATED_FIELD = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/last-researches";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private LastResearchRepository lastResearchRepository;

    @Autowired
    private LastResearchMapper lastResearchMapper;

    @Autowired
    private MockMvc restMockMvc;

    private LastResearch lastResearch;

    private LastResearch insertedLastResearch;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LastResearch createEntity() {
        return new LastResearch()
            .deleted(DEFAULT_DELETED)
            .insertBy(DEFAULT_INSERT_BY)
            .insertDate(DEFAULT_INSERT_DATE)
            .editBy(DEFAULT_EDIT_BY)
            .editDate(DEFAULT_EDIT_DATE)
            .userId(DEFAULT_USER_ID)
            .value(DEFAULT_VALUE)
            .field(DEFAULT_FIELD);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static LastResearch createUpdatedEntity() {
        return new LastResearch()
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .value(UPDATED_VALUE)
            .field(UPDATED_FIELD);
    }

    public static void deleteEntities(LastResearchRepository repository) {
        try {
            repository.deleteAll();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void initTest() {
        lastResearch = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedLastResearch != null) {
            lastResearchRepository.delete(insertedLastResearch);
            insertedLastResearch = null;
        }
        deleteEntities(lastResearchRepository);
    }

    @Test
    void createLastResearch() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);
        MvcResult result = restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isCreated())
            .andReturn();

        LastResearchDTO returnedLastResearchDTO = om.readValue(result.getResponse().getContentAsString(), LastResearchDTO.class);

        // Validate the LastResearch in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedLastResearch = lastResearchMapper.toEntity(returnedLastResearchDTO);
        assertLastResearchUpdatableFieldsEquals(returnedLastResearch, getPersistedLastResearch(returnedLastResearch));

        insertedLastResearch = returnedLastResearch;
    }

    @Test
    void createLastResearchWithExistingId() throws Exception {
        // Create the LastResearch with an existing ID
        lastResearch.setId(1L);
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkUserIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        lastResearch.setUserId(null);

        // Create the LastResearch, which fails.
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllLastResearches() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(lastResearch.getId().intValue())))
            .andExpect(jsonPath("$.[*].deleted").value(hasItem(DEFAULT_DELETED.booleanValue())))
            .andExpect(jsonPath("$.[*].insertBy").value(hasItem(DEFAULT_INSERT_BY)))
            .andExpect(jsonPath("$.[*].insertDate").value(hasItem(sameInstant(DEFAULT_INSERT_DATE))))
            .andExpect(jsonPath("$.[*].editBy").value(hasItem(DEFAULT_EDIT_BY)))
            .andExpect(jsonPath("$.[*].editDate").value(hasItem(sameInstant(DEFAULT_EDIT_DATE))))
            .andExpect(jsonPath("$.[*].userId").value(hasItem(DEFAULT_USER_ID)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.[*].field").value(hasItem(DEFAULT_FIELD)));
    }

    @Test
    void getLastResearch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get the lastResearch
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, lastResearch.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(is(lastResearch.getId().intValue())))
            .andExpect(jsonPath("$.deleted").value(is(DEFAULT_DELETED.booleanValue())))
            .andExpect(jsonPath("$.insertBy").value(is(DEFAULT_INSERT_BY)))
            .andExpect(jsonPath("$.insertDate").value(is(sameInstant(DEFAULT_INSERT_DATE))))
            .andExpect(jsonPath("$.editBy").value(is(DEFAULT_EDIT_BY)))
            .andExpect(jsonPath("$.editDate").value(is(sameInstant(DEFAULT_EDIT_DATE))))
            .andExpect(jsonPath("$.userId").value(is(DEFAULT_USER_ID)))
            .andExpect(jsonPath("$.value").value(is(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.field").value(is(DEFAULT_FIELD)));
    }

    @Test
    void getLastResearchesByIdFiltering() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        Long id = lastResearch.getId();

        defaultLastResearchFiltering("id.equals=" + id, "id.notEquals=" + id);

        defaultLastResearchFiltering("id.greaterThanOrEqual=" + id, "id.greaterThan=" + id);

        defaultLastResearchFiltering("id.lessThanOrEqual=" + id, "id.lessThan=" + id);
    }

    @Test
    void getAllLastResearchesByDeletedIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where deleted equals to
        defaultLastResearchFiltering("deleted.equals=" + DEFAULT_DELETED, "deleted.equals=" + UPDATED_DELETED);
    }

    @Test
    void getAllLastResearchesByDeletedIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where deleted in
        defaultLastResearchFiltering("deleted.in=" + DEFAULT_DELETED + "," + UPDATED_DELETED, "deleted.in=" + UPDATED_DELETED);
    }

    @Test
    void getAllLastResearchesByDeletedIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where deleted is not null
        defaultLastResearchFiltering("deleted.specified=true", "deleted.specified=false");
    }

    @Test
    void getAllLastResearchesByInsertByIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertBy equals to
        defaultLastResearchFiltering("insertBy.equals=" + DEFAULT_INSERT_BY, "insertBy.equals=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLastResearchesByInsertByIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertBy in
        defaultLastResearchFiltering("insertBy.in=" + DEFAULT_INSERT_BY + "," + UPDATED_INSERT_BY, "insertBy.in=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLastResearchesByInsertByIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertBy is not null
        defaultLastResearchFiltering("insertBy.specified=true", "insertBy.specified=false");
    }

    @Test
    void getAllLastResearchesByInsertByContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertBy contains
        defaultLastResearchFiltering("insertBy.contains=" + DEFAULT_INSERT_BY, "insertBy.contains=" + UPDATED_INSERT_BY);
    }

    @Test
    void getAllLastResearchesByInsertByNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertBy does not contain
        defaultLastResearchFiltering("insertBy.doesNotContain=" + UPDATED_INSERT_BY, "insertBy.doesNotContain=" + DEFAULT_INSERT_BY);
    }

    @Test
    void getAllLastResearchesByInsertDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate equals to
        defaultLastResearchFiltering("insertDate.equals=" + DEFAULT_INSERT_DATE, "insertDate.equals=" + UPDATED_INSERT_DATE);
    }

    @Test
    void getAllLastResearchesByInsertDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate in
        defaultLastResearchFiltering(
            "insertDate.in=" + DEFAULT_INSERT_DATE + "," + UPDATED_INSERT_DATE,
            "insertDate.in=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllLastResearchesByInsertDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate is not null
        defaultLastResearchFiltering("insertDate.specified=true", "insertDate.specified=false");
    }

    @Test
    void getAllLastResearchesByInsertDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate is greater than or equal to
        defaultLastResearchFiltering(
            "insertDate.greaterThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.greaterThanOrEqual=" + UPDATED_INSERT_DATE
        );
    }

    @Test
    void getAllLastResearchesByInsertDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate is less than or equal to
        defaultLastResearchFiltering(
            "insertDate.lessThanOrEqual=" + DEFAULT_INSERT_DATE,
            "insertDate.lessThanOrEqual=" + SMALLER_INSERT_DATE
        );
    }

    @Test
    void getAllLastResearchesByInsertDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate is less than
        defaultLastResearchFiltering("insertDate.lessThan=" + UPDATED_INSERT_DATE, "insertDate.lessThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllLastResearchesByInsertDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where insertDate is greater than
        defaultLastResearchFiltering("insertDate.greaterThan=" + SMALLER_INSERT_DATE, "insertDate.greaterThan=" + DEFAULT_INSERT_DATE);
    }

    @Test
    void getAllLastResearchesByEditByIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editBy equals to
        defaultLastResearchFiltering("editBy.equals=" + DEFAULT_EDIT_BY, "editBy.equals=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLastResearchesByEditByIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editBy in
        defaultLastResearchFiltering("editBy.in=" + DEFAULT_EDIT_BY + "," + UPDATED_EDIT_BY, "editBy.in=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLastResearchesByEditByIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editBy is not null
        defaultLastResearchFiltering("editBy.specified=true", "editBy.specified=false");
    }

    @Test
    void getAllLastResearchesByEditByContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editBy contains
        defaultLastResearchFiltering("editBy.contains=" + DEFAULT_EDIT_BY, "editBy.contains=" + UPDATED_EDIT_BY);
    }

    @Test
    void getAllLastResearchesByEditByNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editBy does not contain
        defaultLastResearchFiltering("editBy.doesNotContain=" + UPDATED_EDIT_BY, "editBy.doesNotContain=" + DEFAULT_EDIT_BY);
    }

    @Test
    void getAllLastResearchesByEditDateIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate equals to
        defaultLastResearchFiltering("editDate.equals=" + DEFAULT_EDIT_DATE, "editDate.equals=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllLastResearchesByEditDateIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate in
        defaultLastResearchFiltering("editDate.in=" + DEFAULT_EDIT_DATE + "," + UPDATED_EDIT_DATE, "editDate.in=" + UPDATED_EDIT_DATE);
    }

    @Test
    void getAllLastResearchesByEditDateIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate is not null
        defaultLastResearchFiltering("editDate.specified=true", "editDate.specified=false");
    }

    @Test
    void getAllLastResearchesByEditDateIsGreaterThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate is greater than or equal to
        defaultLastResearchFiltering(
            "editDate.greaterThanOrEqual=" + DEFAULT_EDIT_DATE,
            "editDate.greaterThanOrEqual=" + UPDATED_EDIT_DATE
        );
    }

    @Test
    void getAllLastResearchesByEditDateIsLessThanOrEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate is less than or equal to
        defaultLastResearchFiltering("editDate.lessThanOrEqual=" + DEFAULT_EDIT_DATE, "editDate.lessThanOrEqual=" + SMALLER_EDIT_DATE);
    }

    @Test
    void getAllLastResearchesByEditDateIsLessThanSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate is less than
        defaultLastResearchFiltering("editDate.lessThan=" + UPDATED_EDIT_DATE, "editDate.lessThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllLastResearchesByEditDateIsGreaterThanSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where editDate is greater than
        defaultLastResearchFiltering("editDate.greaterThan=" + SMALLER_EDIT_DATE, "editDate.greaterThan=" + DEFAULT_EDIT_DATE);
    }

    @Test
    void getAllLastResearchesByUserIdIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where userId equals to
        defaultLastResearchFiltering("userId.equals=" + DEFAULT_USER_ID, "userId.equals=" + UPDATED_USER_ID);
    }

    @Test
    void getAllLastResearchesByUserIdIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where userId in
        defaultLastResearchFiltering("userId.in=" + DEFAULT_USER_ID + "," + UPDATED_USER_ID, "userId.in=" + UPDATED_USER_ID);
    }

    @Test
    void getAllLastResearchesByUserIdIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where userId is not null
        defaultLastResearchFiltering("userId.specified=true", "userId.specified=false");
    }

    @Test
    void getAllLastResearchesByUserIdContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where userId contains
        defaultLastResearchFiltering("userId.contains=" + DEFAULT_USER_ID, "userId.contains=" + UPDATED_USER_ID);
    }

    @Test
    void getAllLastResearchesByUserIdNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where userId does not contain
        defaultLastResearchFiltering("userId.doesNotContain=" + UPDATED_USER_ID, "userId.doesNotContain=" + DEFAULT_USER_ID);
    }

    @Test
    void getAllLastResearchesByValueIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where value equals to
        defaultLastResearchFiltering("value.equals=" + DEFAULT_VALUE, "value.equals=" + UPDATED_VALUE);
    }

    @Test
    void getAllLastResearchesByValueIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where value in
        defaultLastResearchFiltering("value.in=" + DEFAULT_VALUE + "," + UPDATED_VALUE, "value.in=" + UPDATED_VALUE);
    }

    @Test
    void getAllLastResearchesByValueIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where value is not null
        defaultLastResearchFiltering("value.specified=true", "value.specified=false");
    }

    @Test
    void getAllLastResearchesByValueContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where value contains
        defaultLastResearchFiltering("value.contains=" + DEFAULT_VALUE, "value.contains=" + UPDATED_VALUE);
    }

    @Test
    void getAllLastResearchesByValueNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where value does not contain
        defaultLastResearchFiltering("value.doesNotContain=" + UPDATED_VALUE, "value.doesNotContain=" + DEFAULT_VALUE);
    }

    @Test
    void getAllLastResearchesByFieldIsEqualToSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where field equals to
        defaultLastResearchFiltering("field.equals=" + DEFAULT_FIELD, "field.equals=" + UPDATED_FIELD);
    }

    @Test
    void getAllLastResearchesByFieldIsInShouldWork() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where field in
        defaultLastResearchFiltering("field.in=" + DEFAULT_FIELD + "," + UPDATED_FIELD, "field.in=" + UPDATED_FIELD);
    }

    @Test
    void getAllLastResearchesByFieldIsNullOrNotNull() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where field is not null
        defaultLastResearchFiltering("field.specified=true", "field.specified=false");
    }

    @Test
    void getAllLastResearchesByFieldContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where field contains
        defaultLastResearchFiltering("field.contains=" + DEFAULT_FIELD, "field.contains=" + UPDATED_FIELD);
    }

    @Test
    void getAllLastResearchesByFieldNotContainsSomething() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        // Get all the lastResearchList where field does not contain
        defaultLastResearchFiltering("field.doesNotContain=" + UPDATED_FIELD, "field.doesNotContain=" + DEFAULT_FIELD);
    }

    private void defaultLastResearchFiltering(String shouldBeFound, String shouldNotBeFound) throws Exception {
        defaultLastResearchShouldBeFound(shouldBeFound);
        defaultLastResearchShouldNotBeFound(shouldNotBeFound);
    }

    /**
     * Executes the search, and checks that the default entity is returned.
     */
    private void defaultLastResearchShouldBeFound(String filter) throws Exception {
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(lastResearch.getId().intValue())))
            .andExpect(jsonPath("$.[*].deleted").value(hasItem(DEFAULT_DELETED.booleanValue())))
            .andExpect(jsonPath("$.[*].insertBy").value(hasItem(DEFAULT_INSERT_BY)))
            .andExpect(jsonPath("$.[*].insertDate").value(hasItem(sameInstant(DEFAULT_INSERT_DATE))))
            .andExpect(jsonPath("$.[*].editBy").value(hasItem(DEFAULT_EDIT_BY)))
            .andExpect(jsonPath("$.[*].editDate").value(hasItem(sameInstant(DEFAULT_EDIT_DATE))))
            .andExpect(jsonPath("$.[*].userId").value(hasItem(DEFAULT_USER_ID)))
            .andExpect(jsonPath("$.[*].value").value(hasItem(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.[*].field").value(hasItem(DEFAULT_FIELD)));

        // Check, that the count call also returns 1
        restMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").value(is(1)));
    }

    /**
     * Executes the search, and checks that the default entity is not returned.
     */
    private void defaultLastResearchShouldNotBeFound(String filter) throws Exception {
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc&" + filter).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        // Check, that the count call also returns 0
        restMockMvc
            .perform(get(ENTITY_API_URL + "/count?sort=id,desc&" + filter).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$").value(is(0)));
    }

    @Test
    void getNonExistingLastResearch() throws Exception {
        // Get the lastResearch
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE).accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void putExistingLastResearch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lastResearch
        LastResearch updatedLastResearch = lastResearchRepository.findByIdAndUserIdAndTenantCode(lastResearch.getId(), "", "").orElse(null);
        updatedLastResearch
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .value(UPDATED_VALUE)
            .field(UPDATED_FIELD);
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(updatedLastResearch);

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, lastResearchDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isOk());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedLastResearchToMatchAllProperties(updatedLastResearch);
    }

    @Test
    void putNonExistingLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, lastResearchDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                put(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().is(405));

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateLastResearchWithPatch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lastResearch using partial update
        LastResearch partialUpdatedLastResearch = new LastResearch();
        partialUpdatedLastResearch.setId(lastResearch.getId());

        partialUpdatedLastResearch.insertDate(UPDATED_INSERT_DATE).userId(UPDATED_USER_ID).value(UPDATED_VALUE);

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLastResearch.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedLastResearch))
            )
            .andExpect(status().isOk());

        // Validate the LastResearch in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLastResearchUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedLastResearch, lastResearch),
            getPersistedLastResearch(lastResearch)
        );
    }

    @Test
    void fullUpdateLastResearchWithPatch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lastResearch using partial update
        LastResearch partialUpdatedLastResearch = new LastResearch();
        partialUpdatedLastResearch.setId(lastResearch.getId());

        partialUpdatedLastResearch
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .value(UPDATED_VALUE)
            .field(UPDATED_FIELD);

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedLastResearch.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedLastResearch))
            )
            .andExpect(status().isOk());

        // Validate the LastResearch in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertLastResearchUpdatableFieldsEquals(partialUpdatedLastResearch, getPersistedLastResearch(partialUpdatedLastResearch));
    }

    @Test
    void patchNonExistingLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, lastResearchDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restMockMvc
            .perform(
                patch(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().is(405));

        // Validate the LastResearch in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteLastResearch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the lastResearch
        restMockMvc
            .perform(
                delete(ENTITY_API_URL_ID, lastResearch.getId())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return lastResearchRepository.count();
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

    protected LastResearch getPersistedLastResearch(LastResearch lastResearch) {
        return lastResearchRepository.findByIdAndUserIdAndTenantCode(lastResearch.getId(), "", "").orElse(null);
    }

    protected void assertPersistedLastResearchToMatchAllProperties(LastResearch expectedLastResearch) {
        // Test fails because reactive api returns an empty object instead of null
        // assertLastResearchAllPropertiesEquals(expectedLastResearch, getPersistedLastResearch(expectedLastResearch));
        assertLastResearchUpdatableFieldsEquals(expectedLastResearch, getPersistedLastResearch(expectedLastResearch));
    }

    protected void assertPersistedLastResearchToMatchUpdatableProperties(LastResearch expectedLastResearch) {
        // Test fails because reactive api returns an empty object instead of null
        // assertLastResearchAllUpdatablePropertiesEquals(expectedLastResearch, getPersistedLastResearch(expectedLastResearch));
        assertLastResearchUpdatableFieldsEquals(expectedLastResearch, getPersistedLastResearch(expectedLastResearch));
    }
}
