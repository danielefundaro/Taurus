package com.fundaro.zodiac.taurus.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
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

import static com.fundaro.zodiac.taurus.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link NoticesResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockTenantUser
class NoticesResourceIT extends TenantAwareResourceIT {

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

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_MESSAGE = "AAAAAAAAAA";
    private static final String UPDATED_MESSAGE = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_READ_DATE = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_READ_DATE = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String ENTITY_API_URL = "/api/notices";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2L * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private NoticesRepository noticesRepository;

    @Autowired
    private NoticesMapper noticesMapper;

    @Autowired
    private MockMvc restMockMvc;

    private Notices notices;

    private Notices insertedNotices;

    /**
     * Create an entity for this test.
     * <p>
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
     * <p>
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

    public static void deleteEntities(NoticesRepository repository) {
        try {
            repository.deleteAll();
        } catch (Exception e) {
            // It can fail, if other entities are still referring this - it will be removed later.
        }
    }

    @BeforeEach
    public void initTest() {
        notices = createEntity();
    }

    @AfterEach
    public void cleanup() {
        if (insertedNotices != null) {
            // La riga puo' essere stata riscritta via API: l'istanza in mano al test e' stale.
            noticesRepository.findById(insertedNotices.getId()).ifPresent(noticesRepository::delete);
            insertedNotices = null;
        }
        deleteEntities(noticesRepository);
    }

    @Test
    void createNotices() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);
        MvcResult result = restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isCreated())
            .andReturn();

        NoticesDTO returnedNoticesDTO = om.readValue(result.getResponse().getContentAsString(), NoticesDTO.class);

        // Validate the Notices in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertThat(returnedNoticesDTO.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(returnedNoticesDTO.getMessage()).isEqualTo(DEFAULT_MESSAGE);

        insertedNotices = noticesRepository.findById(returnedNoticesDTO.getId()).orElseThrow();
        assertThat(insertedNotices.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(insertedNotices.getMessage()).isEqualTo(DEFAULT_MESSAGE);
        assertThat(insertedNotices.getReadDate()).isEqualTo(DEFAULT_READ_DATE);
        // L'audit lo scrive il server: il DTO non trasporta ne' owner ne' campi di tracciamento.
        assertThat(insertedNotices.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedNotices.getInsertBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedNotices.getEditBy()).isEqualTo(TENANT_USER_ID);
        assertThat(insertedNotices.getDeleted()).isFalse();
    }

    @Test
    void createNoticesWithExistingId() throws Exception {
        // Create the Notices with an existing ID
        notices.setId(1L);
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    /**
     * L'owner non arriva mai dal client: userId e' @JsonIgnore sul DTO e il servizio lo riempie con il
     * claim sub. E' cio' che impedisce di creare una riga a nome di un altro utente.
     */
    @Test
    void assignsTheOwnerFromTheAuthenticatedToken() throws Exception {
        notices.setUserId(UPDATED_USER_ID);
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        MvcResult result = restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isCreated())
            .andReturn();

        NoticesDTO returned = om.readValue(result.getResponse().getContentAsString(), NoticesDTO.class);
        insertedNotices = noticesRepository.findById(returned.getId()).orElseThrow();
        assertThat(insertedNotices.getUserId()).isEqualTo(TENANT_USER_ID);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        notices.setName(null);

        // Create the Notices, which fails.
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        restMockMvc
            .perform(
                post(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllNotices() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        // Get all the noticesList
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.content[*].id").value(hasItem(notices.getId().intValue())))
            .andExpect(jsonPath("$.content[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.content[*].message").value(hasItem(DEFAULT_MESSAGE)))
            .andExpect(jsonPath("$.content[*].readDate").value(hasItem(sameInstant(DEFAULT_READ_DATE))));
    }

    @Test
    void getNotices() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        // Get the notices
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, notices.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(is(notices.getId().intValue())))
            .andExpect(jsonPath("$.name").value(is(DEFAULT_NAME)))
            .andExpect(jsonPath("$.message").value(is(DEFAULT_MESSAGE)))
            .andExpect(jsonPath("$.readDate").value(is(sameInstant(DEFAULT_READ_DATE))));
    }

    @Test
    void getNonExistingNotices() throws Exception {
        // Get the notices
        restMockMvc
            .perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE).accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isNotFound());
    }

    @Test
    void putExistingNotices() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Il DTO trasporta i campi di contenuto: e' tutto cio' che il client puo' cambiare.
        Notices updatedNotices = getPersistedNotices(notices);
        updatedNotices.name(UPDATED_NAME).message(UPDATED_MESSAGE).readDate(UPDATED_READ_DATE);
        NoticesDTO noticesDTO = noticesMapper.toDto(updatedNotices);

        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, noticesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isOk());

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedNoticesWasUpdatedByTheServer(UPDATED_NAME, UPDATED_MESSAGE, UPDATED_READ_DATE);
    }

    @Test
    void putNonExistingNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, noticesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isNotFound());

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
        restMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isBadRequest());

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
        restMockMvc
            .perform(
                put(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().is(405));

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateNoticesWithPatch() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the notices using partial update
        Notices partialUpdatedNotices = new Notices();
        partialUpdatedNotices.setId(notices.getId());

        partialUpdatedNotices.message(UPDATED_MESSAGE);

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedNotices.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedNotices))
            )
            .andExpect(status().isOk());

        // Validate the Notices in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        // I campi non inviati restano quelli di partenza.
        assertPersistedNoticesWasUpdatedByTheServer(DEFAULT_NAME, UPDATED_MESSAGE, DEFAULT_READ_DATE);
    }

    @Test
    void fullUpdateNoticesWithPatch() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the notices using partial update
        Notices partialUpdatedNotices = new Notices();
        partialUpdatedNotices.setId(notices.getId());

        // Anche inviando i campi di audit, il servizio ignora tutto cio' che non e' sul DTO.
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

        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedNotices.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(partialUpdatedNotices))
            )
            .andExpect(status().isOk());

        // Validate the Notices in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedNoticesWasUpdatedByTheServer(UPDATED_NAME, UPDATED_MESSAGE, UPDATED_READ_DATE);
    }

    @Test
    void patchNonExistingNotices() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        notices.setId(longCount.incrementAndGet());

        // Create the Notices
        NoticesDTO noticesDTO = noticesMapper.toDto(notices);

        // L'id e' coerente con il path ma non esiste: il servizio non trova la riga posseduta.
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, noticesDTO.getId())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isNotFound());

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
        restMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().isBadRequest());

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
        restMockMvc
            .perform(
                patch(ENTITY_API_URL)
                    .with(csrf())
                    .contentType(MediaType.valueOf("application/merge-patch+json"))
                    .content(om.writeValueAsBytes(noticesDTO))
            )
            .andExpect(status().is(405));

        // Validate the Notices in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteNotices() throws Exception {
        // Initialize the database
        insertedNotices = noticesRepository.save(notices);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the notices
        restMockMvc
            .perform(
                delete(ENTITY_API_URL_ID, notices.getId())
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNoContent());

        // La cancellazione e' logica: la riga resta a database ma esce dalle letture dell'API.
        assertSameRepositoryCount(databaseSizeBeforeDelete);
        assertThat(noticesRepository.findById(notices.getId()).orElseThrow().getDeleted()).isTrue();
        restMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[*].id").value(not(hasItem(notices.getId().intValue()))));
    }

    protected long getRepositoryCount() {
        return noticesRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Notices getPersistedNotices(Notices notices) {
        return noticesRepository.findByIdAndUserId(notices.getId(), TENANT_USER_ID).orElse(null);
    }

    /**
     * Verifica cosa cambia dopo una scrittura: i campi del DTO seguono la richiesta, l'audit no.
     * insertBy resta quello originale, mentre owner ed editBy sono riscritti con l'identita' del token.
     */
    private void assertPersistedNoticesWasUpdatedByTheServer(
        String expectedName,
        String expectedMessage,
        ZonedDateTime expectedReadDate
    ) {
        Notices persisted = getPersistedNotices(notices);
        assertThat(persisted.getName()).isEqualTo(expectedName);
        assertThat(persisted.getMessage()).isEqualTo(expectedMessage);
        assertThat(persisted.getReadDate()).isEqualTo(expectedReadDate);
        assertThat(persisted.getInsertBy()).isEqualTo(DEFAULT_INSERT_BY);
        assertThat(persisted.getUserId()).isEqualTo(TENANT_USER_ID);
        assertThat(persisted.getEditBy()).isEqualTo(TENANT_USER_ID);
    }
}
