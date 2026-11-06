package com.fundaro.zodiac.taurus.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.LegalDocument;
import com.fundaro.zodiac.taurus.domain.UserLegalAcceptance;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import com.fundaro.zodiac.taurus.repository.LegalDocumentRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.legal.LegalContent;
import com.fundaro.zodiac.taurus.test.util.WithMockTenantUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of the legal documents on PostgreSQL: versions written by the super administrator, immutable
 * from their publication, read by anyone without login and confirmed with the fingerprint of their text.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockTenantUser(authorities = "ROLE_SUPER_ADMIN", role = "ROLE_SUPER_ADMIN")
class LegalResourceIT extends TenantAwareResourceIT {

    private static final String ADMIN_URL = "/api/legal/documents";
    private static final String PUBLIC_URL = "/api/legal-documents/v1";
    private static final String TEXT = "# Termini\n\nL'accesso avviene tramite account. <script>x()</script>";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LegalDocumentRepository documentRepository;

    @Autowired
    private UserLegalAcceptanceRepository acceptanceRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        acceptanceRepository.deleteAllInBatch();
        documentRepository.deleteAllInBatch();
    }

    @Test
    void publishesADraftThatNoLongerChanges() throws Exception {
        long id = create("1.3", TEXT);
        mockMvc.perform(get(PUBLIC_URL + "/terms/1.3")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("error.legal.document.notFound"));

        mockMvc
            .perform(post(ADMIN_URL + "/" + id + "/activation").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ACTIVE"))
            .andExpect(jsonPath("$.publishedAt").isNotEmpty())
            .andExpect(jsonPath("$.contentSha256").value(LegalContent.sha256(TEXT)));

        mockMvc
            .perform(put(ADMIN_URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto("1.3", "Altro testo"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.legal.document.published.immutable"));
        mockMvc
            .perform(delete(ADMIN_URL + "/" + id).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.legal.document.published.immutable"));
        assertThat(documentRepository.findById(id)).get().extracting(LegalDocument::getContent).isEqualTo(TEXT);
    }

    @Test
    void changesAndDeletesADraft() throws Exception {
        long id = create("1.3", TEXT);

        mockMvc
            .perform(put(ADMIN_URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto("1.4", "Nuovo testo"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value("1.4"))
            .andExpect(jsonPath("$.contentSha256").value(LegalContent.sha256("Nuovo testo")));
        mockMvc.perform(get(ADMIN_URL)).andExpect(jsonPath("$[0].state").value("DRAFT")).andExpect(jsonPath("$[0].content").value(nullValue()));
        mockMvc.perform(get(ADMIN_URL + "/" + id)).andExpect(jsonPath("$.content").value("Nuovo testo"));

        mockMvc.perform(delete(ADMIN_URL + "/" + id).with(csrf())).andExpect(status().isNoContent());
        assertThat(documentRepository.findById(id)).isEmpty();
    }

    @Test
    void rejectsAnInvalidText() throws Exception {
        mockMvc
            .perform(post(ADMIN_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto("1 3", TEXT))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.legal.document.version.invalid"));
        mockMvc
            .perform(post(ADMIN_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto("1.3", " "))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.legal.document.content.required"));
        assertThat(documentRepository.count()).isZero();
    }

    @Test
    @WithUnauthenticatedMockUser
    void hidesTheDocumentsThatAreNotPublished() throws Exception {
        mockMvc.perform(get(PUBLIC_URL + "/terms")).andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLIC_URL + "/cookies")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("error.legal.document.notFound"));
    }

    @Test
    void letsAnyoneReadThePublishedVersions() throws Exception {
        long first = publish(create("1.2", "Testo **precedente**"));
        long second = publish(create("1.3", TEXT));
        String expectedHtml = "<h2 id=\"termini\">Termini</h2>\n<p>L'accesso avviene tramite account. &lt;script&gt;x()&lt;/script&gt;</p>";

        MvcResult inForce = mockMvc
            .perform(get(PUBLIC_URL + "/terms").with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
            .andExpect(jsonPath("$.version").value("1.3"))
            .andExpect(jsonPath("$.active").value(true))
            .andExpect(jsonPath("$.html").value(expectedHtml))
            .andExpect(jsonPath("$.contentSha256").value(LegalContent.sha256(TEXT)))
            .andExpect(jsonPath("$.versions.length()").value(2))
            .andExpect(jsonPath("$.versions[0].version").value("1.3"))
            .andExpect(jsonPath("$.versions[1].active").value(false))
            .andReturn();
        String etag = inForce.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        mockMvc.perform(get(PUBLIC_URL + "/terms").with(anonymous()).header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified());

        mockMvc
            .perform(get(PUBLIC_URL + "/terms/1.2").with(anonymous()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false))
            .andExpect(jsonPath("$.html").value("<p>Testo <strong>precedente</strong></p>"));
        assertThat(documentRepository.findById(first)).get().extracting(LegalDocument::getActive).isEqualTo(false);
        assertThat(documentRepository.findById(second)).get().extracting(LegalDocument::getActive).isEqualTo(true);
    }

    @Test
    void recordsTheFingerprintOfTheConfirmedText() throws Exception {
        long id = publish(create("1.3", TEXT));

        mockMvc
            .perform(get("/api/legal/status"))
            .andExpect(jsonPath("$.compliant").value(false))
            .andExpect(jsonPath("$.documents[0].url").value(nullValue()))
            .andExpect(jsonPath("$.documents[0].contentSha256").value(LegalContent.sha256(TEXT)))
            .andExpect(jsonPath("$.documents[0].previousVersionAccepted").value(false));
        mockMvc
            .perform(
                post("/api/legal/acceptances")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(new LegalAcceptanceRequestDTO(Set.of(id))))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compliant").value(true));

        assertThat(acceptanceRepository.findAll()).singleElement().extracting(UserLegalAcceptance::getContentSha256).isEqualTo(LegalContent.sha256(TEXT));

        publish(create("1.4", TEXT + "\n\nNuova sezione."));
        mockMvc
            .perform(get("/api/legal/status"))
            .andExpect(jsonPath("$.compliant").value(false))
            .andExpect(jsonPath("$.documents[0].version").value("1.4"))
            .andExpect(jsonPath("$.documents[0].previousVersionAccepted").value(true));
    }

    @Test
    void addsOnceTheTextOfADocumentPublishedWithItsAddress() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO public.legal_document (document_type, version, title, url, action, published_at, active, required) " +
            "VALUES ('PRIVACY', '1.1', 'Informativa privacy', 'https://example.test/privacy', 'ACKNOWLEDGE', now(), true, true)"
        );
        long id = documentRepository.findByDocumentTypeAndVersion(LegalDocumentType.PRIVACY, "1.1").orElseThrow().getId();
        mockMvc.perform(get(PUBLIC_URL + "/privacy")).andExpect(jsonPath("$.url").value("https://example.test/privacy")).andExpect(jsonPath("$.html").value(nullValue()));
        LegalDocumentDTO completion = privacy("Testo dell'informativa");

        mockMvc
            .perform(put(ADMIN_URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(completion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ACTIVE"))
            .andExpect(jsonPath("$.url").value("https://example.test/privacy"))
            .andExpect(jsonPath("$.contentSha256").value(LegalContent.sha256("Testo dell'informativa")));
        mockMvc.perform(get(PUBLIC_URL + "/privacy")).andExpect(jsonPath("$.url").value(nullValue())).andExpect(jsonPath("$.html").value("<p>Testo dell'informativa</p>"));

        mockMvc
            .perform(put(ADMIN_URL + "/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(privacy("Altro testo"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.legal.document.published.immutable"));
    }

    @Test
    void keepsTheRulesOfTheVersionsInTheDatabase() {
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO public.legal_document (document_type, version, title, content, content_sha256, action, active, required) " +
                "VALUES ('TERMS', '9', 'Bozza', 'x', '" + LegalContent.sha256("x") + "', 'ACCEPT', true, true)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO public.legal_document (document_type, version, title, action, active, required) VALUES ('TERMS', '9', 'Vuoto', 'ACCEPT', false, true)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            jdbcTemplate.update(
                "INSERT INTO public.legal_document (document_type, version, title, content, action, active, required) VALUES ('TERMS', '9', 'Senza impronta', 'x', 'ACCEPT', false, true)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @WithMockTenantUser(authorities = "ROLE_ADMIN", role = "ROLE_ADMIN")
    void keepsTheVersionsToTheSuperAdministrator() throws Exception {
        mockMvc.perform(get(ADMIN_URL)).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_URL + "/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"x\"}")).andExpect(status().isForbidden());
    }

    @Test
    void previewsTheText() throws Exception {
        mockMvc
            .perform(post(ADMIN_URL + "/preview").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"# Titolo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.html").value("<h2 id=\"titolo\">Titolo</h2>"));
    }

    private long create(String version, String content) throws Exception {
        MvcResult result = mockMvc
            .perform(post(ADMIN_URL).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dto(version, content))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value("DRAFT"))
            .andExpect(jsonPath("$.publishedAt").value(nullValue()))
            .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long publish(long id) throws Exception {
        mockMvc.perform(post(ADMIN_URL + "/" + id + "/activation").with(csrf())).andExpect(status().isOk());
        return id;
    }

    private static LegalDocumentDTO dto(String version, String content) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setDocumentType(LegalDocumentType.TERMS);
        dto.setVersion(version);
        dto.setTitle("Termini di utilizzo");
        dto.setAction(LegalDocumentAction.ACCEPT);
        dto.setRequired(true);
        dto.setContent(content);
        return dto;
    }

    private static LegalDocumentDTO privacy(String content) {
        LegalDocumentDTO dto = new LegalDocumentDTO();
        dto.setDocumentType(LegalDocumentType.PRIVACY);
        dto.setVersion("1.1");
        dto.setTitle("Informativa privacy");
        dto.setAction(LegalDocumentAction.ACKNOWLEDGE);
        dto.setRequired(true);
        dto.setContent(content);
        return dto;
    }
}
