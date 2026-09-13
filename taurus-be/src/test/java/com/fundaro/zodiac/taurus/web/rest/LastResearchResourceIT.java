package com.fundaro.zodiac.taurus.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.service.dto.LastResearchDTO;
import com.fundaro.zodiac.taurus.service.mapper.LastResearchMapper;
import com.fundaro.zodiac.taurus.test.util.WithMockTenantUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link LastResearchResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockTenantUser
class LastResearchResourceIT extends TenantAwareResourceIT {

    private static final Boolean DEFAULT_DELETED = false;
    private static final Boolean UPDATED_DELETED = true;

    private static final String DEFAULT_INSERT_BY = "AAAAAAAAAA";
    private static final String UPDATED_INSERT_BY = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_INSERT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_INSERT_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_EDIT_BY = "AAAAAAAAAA";
    private static final String UPDATED_EDIT_BY = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_EDIT_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_EDIT_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_USER_ID = "AAAAAAAAAA";
    private static final String UPDATED_USER_ID = "BBBBBBBBBB";

    private static final String DEFAULT_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_VALUE = "BBBBBBBBBB";

    private static final String DEFAULT_FIELD = "AAAAAAAAAA";
    private static final String UPDATED_FIELD = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/last-researches";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

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
     * <p>
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
     * <p>
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
            // La riga puo' essere stata riscritta via API: l'istanza in mano al test e' stale.
            lastResearchRepository.findById(insertedLastResearch.getId()).ifPresent(lastResearchRepository::delete);
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
        assertThat(returnedLastResearchDTO.getValue()).isEqualTo(DEFAULT_VALUE);
        assertThat(returnedLastResearchDTO.getField()).isEqualTo(DEFAULT_FIELD);

        insertedLastResearch = lastResearchRepository.findById(returnedLastResearchDTO.getId()).orElseThrow();
        assertThat(insertedLastResearch.getValue()).isEqualTo(DEFAULT_VALUE);
        assertThat(insertedLastResearch.getField()).isEqualTo(DEFAULT_FIELD);
        // L'audit lo scrive il server: il DTO non trasporta ne' owner ne' campi di tracciamento.
        assertThat(insertedLastResearch.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedLastResearch.getInsertBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedLastResearch.getEditBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedLastResearch.getDeleted()).isFalse();
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

    /**
     * L'owner non arriva mai dal client: userId e' @JsonIgnore sul DTO e il servizio lo riempie con il
     * claim sub. E' cio' che impedisce di creare una riga a nome di un altro utente.
     */
    @Test
    void assignsTheOwnerFromTheAuthenticatedToken() throws Exception {
        lastResearch.setUserId(UPDATED_USER_ID);
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

        LastResearchDTO returned = om.readValue(result.getResponse().getContentAsString(), LastResearchDTO.class);
        insertedLastResearch = lastResearchRepository.findById(returned.getId()).orElseThrow();
        assertThat(insertedLastResearch.getUserId()).isEqualTo(TENANT_USER_ID);
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
            .andExpect(jsonPath("$.content[*].id").value(hasItem(lastResearch.getId().intValue())))
            .andExpect(jsonPath("$.content[*].value").value(hasItem(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.content[*].field").value(hasItem(DEFAULT_FIELD)));
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
            .andExpect(jsonPath("$.value").value(is(DEFAULT_VALUE)))
            .andExpect(jsonPath("$.field").value(is(DEFAULT_FIELD)));
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

        // Il DTO trasporta solo value e field: e' tutto cio' che il client puo' cambiare.
        LastResearch updatedLastResearch = getPersistedLastResearch(lastResearch);
        updatedLastResearch.value(UPDATED_VALUE).field(UPDATED_FIELD);
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
        assertPersistedLastResearchWasUpdatedByTheServer(UPDATED_VALUE, UPDATED_FIELD);
    }

    @Test
    void putNonExistingLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, lastResearchDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isNotFound());

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

        partialUpdatedLastResearch.value(UPDATED_VALUE);

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
        // I campi non inviati restano quelli di partenza.
        assertPersistedLastResearchWasUpdatedByTheServer(UPDATED_VALUE, DEFAULT_FIELD);
    }

    @Test
    void fullUpdateLastResearchWithPatch() throws Exception {
        // Initialize the database
        insertedLastResearch = lastResearchRepository.save(lastResearch);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the lastResearch using partial update
        LastResearch partialUpdatedLastResearch = new LastResearch();
        partialUpdatedLastResearch.setId(lastResearch.getId());

        // Anche inviando i campi di audit, il servizio ignora tutto cio' che non e' sul DTO.
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
        assertPersistedLastResearchWasUpdatedByTheServer(UPDATED_VALUE, UPDATED_FIELD);
    }

    @Test
    void patchNonExistingLastResearch() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        lastResearch.setId(longCount.incrementAndGet());

        // Create the LastResearch
        LastResearchDTO lastResearchDTO = lastResearchMapper.toDto(lastResearch);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, lastResearchDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(lastResearchDTO))
            )
            .andExpect(status().isNotFound());

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

        // La cancellazione e' logica: la riga resta a database ma esce dalle letture dell'API.
        assertSameRepositoryCount(databaseSizeBeforeDelete);
        assertThat(lastResearchRepository.findById(lastResearch.getId()).orElseThrow().getDeleted()).isTrue();
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id").value(not(hasItem(lastResearch.getId().intValue()))));
    }

    protected long getRepositoryCount() {
        return lastResearchRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected LastResearch getPersistedLastResearch(LastResearch lastResearch) {
        return lastResearchRepository.findByIdAndUserId(lastResearch.getId(), TENANT_USER_ID).orElse(null);
    }

    /**
     * Verifica cosa cambia dopo una scrittura: i due campi del DTO seguono la richiesta, l'audit no.
     * insertBy resta quello originale, mentre owner ed editBy sono riscritti con l'identita' del token.
     */
    private void assertPersistedLastResearchWasUpdatedByTheServer(String expectedValue, String expectedField) {
        LastResearch persisted = getPersistedLastResearch(lastResearch);
        assertThat(persisted.getValue()).isEqualTo(expectedValue);
        assertThat(persisted.getField()).isEqualTo(expectedField);
        assertThat(persisted.getInsertBy()).isEqualTo(DEFAULT_INSERT_BY);
        assertThat(persisted.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(persisted.getEditBy()).isEqualTo(TENANT_USER_ID);
    }
}
