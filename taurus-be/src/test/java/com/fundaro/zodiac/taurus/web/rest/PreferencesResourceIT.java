package com.fundaro.zodiac.taurus.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import com.fundaro.zodiac.taurus.service.mapper.PreferencesMapper;
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
 * Integration tests for the {@link PreferencesResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockTenantUser
class PreferencesResourceIT extends TenantAwareResourceIT {

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

    private static final String DEFAULT_KEY = "AAAAAAAAAA";
    private static final String UPDATED_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_VALUE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/preferences";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PreferencesRepository preferencesRepository;

    @Autowired
    private PreferencesMapper preferencesMapper;

    @Autowired
    private MockMvc restMockMvc;

    private Preferences preferences;

    private Preferences insertedPreferences;

    /**
     * Create an entity for this test.
     * <p>
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
     * <p>
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

    public static void deleteEntities(PreferencesRepository repository) {
        try {
            repository.deleteAll();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void initTest() {
        preferences = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedPreferences != null) {
            // La riga puo' essere stata riscritta via API: l'istanza in mano al test e' stale.
            preferencesRepository.findById(insertedPreferences.getId()).ifPresent(preferencesRepository::delete);
            insertedPreferences = null;
        }
        deleteEntities(preferencesRepository);
    }

    @Test
    void createPreferences() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);
        MvcResult result = restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isCreated())
            .andReturn();

        PreferencesDTO returnedPreferencesDTO = om.readValue(result.getResponse().getContentAsString(), PreferencesDTO.class);

        // Validate the Preferences in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedPreferencesDTO.getKey()).isEqualTo(DEFAULT_KEY);
        assertThat(returnedPreferencesDTO.getValue()).isEqualTo(DEFAULT_VALUE);

        insertedPreferences = preferencesRepository.findById(returnedPreferencesDTO.getId()).orElseThrow();
        assertThat(insertedPreferences.getKey()).isEqualTo(DEFAULT_KEY);
        assertThat(insertedPreferences.getValue()).isEqualTo(DEFAULT_VALUE);
        // L'audit lo scrive il server: il DTO non trasporta ne' owner ne' campi di tracciamento.
        assertThat(insertedPreferences.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedPreferences.getInsertBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedPreferences.getEditBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedPreferences.getDeleted()).isFalse();
    }

    @Test
    void createPreferencesWithExistingId() throws Exception {
        // Create the Preferences with an existing ID
        preferences.setId(1L);
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    /**
     * L'owner non arriva mai dal client: userId e' @JsonIgnore sul DTO e il servizio lo riempie con il
     * claim sub. E' cio' che impedisce di creare una riga a nome di un altro utente.
     */
    @Test
    void assignsTheOwnerFromTheAuthenticatedToken() throws Exception {
        preferences.setUserId(UPDATED_USER_ID);
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        MvcResult result = restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isCreated())
            .andReturn();

        PreferencesDTO returned = om.readValue(result.getResponse().getContentAsString(), PreferencesDTO.class);
        insertedPreferences = preferencesRepository.findById(returned.getId()).orElseThrow();
        assertThat(insertedPreferences.getUserId()).isEqualTo(TENANT_USER_ID);
    }

    @Test
    void checkKeyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        preferences.setKey(null);

        // Create the Preferences, which fails.
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPreferences() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        // Get all the preferencesList
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.content[*].id").value(hasItem(preferences.getId().intValue())))
            .andExpect(jsonPath("$.content[*].key").value(hasItem(DEFAULT_KEY)))
            .andExpect(jsonPath("$.content[*].value").value(hasItem(DEFAULT_VALUE)));
    }

    @Test
    void getPreferences() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        // Get the preferences
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, preferences.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(is(preferences.getId().intValue())))
            .andExpect(jsonPath("$.key").value(is(DEFAULT_KEY)))
            .andExpect(jsonPath("$.value").value(is(DEFAULT_VALUE)));
    }

    @Test
    void getNonExistingPreferences() throws Exception {
        // Get the preferences
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE).accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void putExistingPreferences() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Il DTO trasporta solo key e value: e' tutto cio' che il client puo' cambiare.
        Preferences updatedPreferences = getPersistedPreferences(preferences);
        updatedPreferences.key(UPDATED_KEY).value(UPDATED_VALUE);
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(updatedPreferences);

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, preferencesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isOk());

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPreferencesWasUpdatedByTheServer(UPDATED_KEY, UPDATED_VALUE);
    }

    @Test
    void putNonExistingPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, preferencesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isNotFound());

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
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isBadRequest());

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
        restMockMvc
            .perform(
                put(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().is(405));

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePreferencesWithPatch() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the preferences using partial update
        Preferences partialUpdatedPreferences = new Preferences();
        partialUpdatedPreferences.setId(preferences.getId());

        partialUpdatedPreferences.value(UPDATED_VALUE);

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPreferences.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedPreferences))
            )
            .andExpect(status().isOk());

        // Validate the Preferences in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        // I campi non inviati restano quelli di partenza.
        assertPersistedPreferencesWasUpdatedByTheServer(DEFAULT_KEY, UPDATED_VALUE);
    }

    @Test
    void fullUpdatePreferencesWithPatch() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the preferences using partial update
        Preferences partialUpdatedPreferences = new Preferences();
        partialUpdatedPreferences.setId(preferences.getId());

        // Anche inviando i campi di audit, il servizio ignora tutto cio' che non e' sul DTO.
        partialUpdatedPreferences
            .deleted(UPDATED_DELETED)
            .insertBy(UPDATED_INSERT_BY)
            .insertDate(UPDATED_INSERT_DATE)
            .editBy(UPDATED_EDIT_BY)
            .editDate(UPDATED_EDIT_DATE)
            .userId(UPDATED_USER_ID)
            .key(UPDATED_KEY)
            .value(UPDATED_VALUE);

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPreferences.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedPreferences))
            )
            .andExpect(status().isOk());

        // Validate the Preferences in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPreferencesWasUpdatedByTheServer(UPDATED_KEY, UPDATED_VALUE);
    }

    @Test
    void patchNonExistingPreferences() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        preferences.setId(longCount.incrementAndGet());

        // Create the Preferences
        PreferencesDTO preferencesDTO = preferencesMapper.toDto(preferences);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, preferencesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isNotFound());

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
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().isBadRequest());

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
        restMockMvc
            .perform(
                patch(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(preferencesDTO))
            )
            .andExpect(status().is(405));

        // Validate the Preferences in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePreferences() throws Exception {
        // Initialize the database
        insertedPreferences = preferencesRepository.save(preferences);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the preferences
        restMockMvc
            .perform(
                delete(ENTITY_API_URL_ID, preferences.getId())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNoContent());

        // La cancellazione e' logica: la riga resta a database ma esce dalle letture dell'API.
        assertSameRepositoryCount(databaseSizeBeforeDelete);
        assertThat(preferencesRepository.findById(preferences.getId()).orElseThrow().getDeleted()).isTrue();
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id").value(not(hasItem(preferences.getId().intValue()))));
    }

    protected long getRepositoryCount() {
        return preferencesRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Preferences getPersistedPreferences(Preferences preferences) {
        return preferencesRepository.findByIdAndUserId(preferences.getId(), TENANT_USER_ID).orElse(null);
    }

    /**
     * Verifica cosa cambia dopo una scrittura: i due campi del DTO seguono la richiesta, l'audit no.
     * insertBy resta quello originale, mentre owner ed editBy sono riscritti con l'identita' del token.
     */
    private void assertPersistedPreferencesWasUpdatedByTheServer(String expectedKey, String expectedValue) {
        Preferences persisted = getPersistedPreferences(preferences);
        assertThat(persisted.getKey()).isEqualTo(expectedKey);
        assertThat(persisted.getValue()).isEqualTo(expectedValue);
        assertThat(persisted.getInsertBy()).isEqualTo(DEFAULT_INSERT_BY);
        assertThat(persisted.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(persisted.getEditBy()).isEqualTo(TENANT_USER_ID);
    }
}
