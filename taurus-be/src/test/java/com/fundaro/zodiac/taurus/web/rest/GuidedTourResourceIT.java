package com.fundaro.zodiac.taurus.web.rest;

import tools.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProfileRepository;
import com.fundaro.zodiac.taurus.repository.guidedtour.GuidedTourProgressRepository;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressRequest;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsRequest;
import com.fundaro.zodiac.taurus.test.util.WithMockTenantUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@link GuidedTourResource} REST controller: person, role and tenant come from the token,
 * the body carries only settings and progress.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockTenantUser(role = "ROLE_USER")
class GuidedTourResourceIT extends TenantAwareResourceIT {

    private static final String API_URL = "/api/guided-tour";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private GuidedTourProfileRepository profileRepository;

    @Autowired
    private GuidedTourProgressRepository progressRepository;

    @AfterEach
    void cleanup() {
        progressRepository.deleteAllInBatch();
        profileRepository.deleteAllInBatch();
    }

    @Test
    void readsTheDefaultsWithoutWriting() throws Exception {
        restMockMvc
            .perform(get(API_URL))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.role").value("ROLE_USER"))
            .andExpect(jsonPath("$.settings.autoStartEnabled").value(true))
            .andExpect(jsonPath("$.settings.legacyInvitation").value(false))
            .andExpect(jsonPath("$.settings.version").value(nullValue()))
            .andExpect(jsonPath("$.progress").value(nullValue()));

        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void savesTheSettingsOfThePerson() throws Exception {
        putSettings(new GuidedTourSettingsRequest(false, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.settings.autoStartEnabled").value(false))
            .andExpect(jsonPath("$.settings.version").value(0));

        restMockMvc.perform(get(API_URL)).andExpect(jsonPath("$.settings.autoStartEnabled").value(false));
        assertThat(profileRepository.findByKeycloakSubjectAndDeletedFalse(TENANT_USER_ID)).isPresent();
    }

    @Test
    void rejectsSettingsWithoutTheSwitch() throws Exception {
        putSettings(new GuidedTourSettingsRequest(null, null))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.validation"));

        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void savesAndRestartsTheProgressOfTheRole() throws Exception {
        putProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of("welcome.intro"), "welcome.intro", 1, 1, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.progress.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.progress.seenSteps", contains("welcome.intro")))
            .andExpect(jsonPath("$.progress.version").value(0));

        restMockMvc
            .perform(delete(API_URL + "/progress").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.progress").value(nullValue()));

        assertThat(progressRepository.count()).isZero();
        assertThat(profileRepository.count()).isOne();
    }

    @Test
    void answersAConflictToAStaleProgress() throws Exception {
        putProgress(new GuidedTourProgressRequest("IN_PROGRESS", List.of("welcome.intro"), "welcome.intro", 1, 1, null))
            .andExpect(status().isOk());

        putProgress(new GuidedTourProgressRequest("COMPLETED", List.of("welcome.intro"), "welcome.intro", 1, 1, null))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.guidedTour.versionConflict"));
    }

    @Test
    @WithMockTenantUser
    void refusesATokenWithoutRole() throws Exception {
        restMockMvc
            .perform(get(API_URL))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.auth.forbidden"));
    }

    private ResultActions putSettings(GuidedTourSettingsRequest request) throws Exception {
        return restMockMvc.perform(
            put(API_URL + "/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request))
        );
    }

    private ResultActions putProgress(GuidedTourProgressRequest request) throws Exception {
        return restMockMvc.perform(
            put(API_URL + "/progress").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request))
        );
    }
}
