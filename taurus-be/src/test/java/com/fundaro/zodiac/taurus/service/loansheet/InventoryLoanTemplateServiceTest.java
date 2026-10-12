package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSettings;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplate;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanTemplateOrigin;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSettingsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanTemplateRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.*;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryLoanTemplateServiceTest {

    @Mock
    InventoryLoanTemplateRepository templateRepository;
    @Mock
    InventoryLoanSettingsRepository settingsRepository;
    @Mock
    LoanSheetTenantData tenantData;
    @Mock
    TenantTimeZoneService tenantTimeZoneService;

    private final LoanTemplateDefaults defaults = new LoanTemplateDefaults();
    private InventoryLoanTemplateService service;
    private InventoryLoanSettings settings;

    @BeforeEach
    void setUp() {
        service = new InventoryLoanTemplateService(templateRepository, settingsRepository, defaults, new LoanSheetPdfRenderer(), tenantData, tenantTimeZoneService);
        settings = new InventoryLoanSettings();
        settings.setId(1L);
        settings.setMinMonths(12);
        settings.setMaxMonths(24);
        settings.setRenewalMaxMonths(24);
        settings.setTotalMaxMonths(36);
        when(settingsRepository.findFirstByDeletedFalse()).thenReturn(Optional.of(settings));
        when(settingsRepository.saveAndFlush(any(InventoryLoanSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(templateRepository.saveAndFlush(any(InventoryLoanTemplate.class))).thenAnswer(invocation -> {
            InventoryLoanTemplate template = invocation.getArgument(0);
            if (template.getId() == null) template.setId(50L);
            return template;
        });
        when(templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(any(), anyString())).thenReturn(Optional.empty());
        when(templateRepository.findAllByOriginAndDeletedFalseOrderByVersionNumberDesc(InventoryLoanTemplateOrigin.CUSTOM)).thenReturn(List.of());
        when(tenantData.forTemplate(anyString(), any())).thenReturn(LoanSheetPdfRendererTest.TENANT);
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
    }

    @Test
    void createsTheSettingsWithTheDefaultDurationsOnFirstUse() {
        when(settingsRepository.findFirstByDeletedFalse()).thenReturn(Optional.empty());
        when(settingsRepository.saveAndFlush(any(InventoryLoanSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryLoanSettings created = service.settings("admin");

        assertThat(InventoryLoanTemplateService.durations(created)).isEqualTo(defaults.durations());
        assertThat(created.getActiveTemplate()).isNull();
        verify(templateRepository).lockTenantTemplates();
    }

    @Test
    void activatesAValidTemplateAsANewVersion() {
        when(templateRepository.findMaxVersionNumber()).thenReturn(2);
        String content = defaults.content().replace("<body>", "<body><!-- modello del tenant -->");

        Overview overview = service.activate(new TemplateFile("C:\\fakepath\\modello banda.html", content), token());

        ArgumentCaptor<InventoryLoanTemplate> saved = ArgumentCaptor.forClass(InventoryLoanTemplate.class);
        verify(templateRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getVersionNumber()).isEqualTo(3);
        assertThat(saved.getValue().getOrigin()).isEqualTo(InventoryLoanTemplateOrigin.CUSTOM);
        assertThat(saved.getValue().getFileName()).isEqualTo("modello banda.html");
        assertThat(saved.getValue().getContentHash()).isEqualTo(LoanSheetSupport.sha256(content));
        assertThat(settings.getActiveTemplate()).isSameAs(saved.getValue());
        assertThat(overview.active().versionNumber()).isEqualTo(3);
        assertThat(overview.active().active()).isTrue();
        verify(templateRepository).lockTenantTemplates();
    }

    @Test
    void reactivatesTheExistingVersionOfAnIdenticalFile() {
        InventoryLoanTemplate existing = template(7L, 2, InventoryLoanTemplateOrigin.CUSTOM);
        when(templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(eq(InventoryLoanTemplateOrigin.CUSTOM), anyString())).thenReturn(Optional.of(existing));

        service.activate(new TemplateFile("modello.html", defaults.content()), token());

        verify(templateRepository, never()).saveAndFlush(any());
        assertThat(settings.getActiveTemplate()).isSameAs(existing);
    }

    @Test
    void refusesToActivateOrPreviewATemplateWithBlockingIssues() {
        TemplateFile file = new TemplateFile("modello.html", "<p>{{associazione.sconosciuta}}</p>");

        assertThatThrownBy(() -> service.activate(file, token()))
            .isInstanceOf(RequestAlertException.class)
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanTemplate.invalid");
                assertThat(exception.getArgs().values().get("issues")).isEqualTo(java.util.List.of(java.util.Map.of(
                    "code", "unknownVariable",
                    "line", 1,
                    "args", java.util.Map.of("name", "associazione.sconosciuta")
                )));
            });
        assertThatThrownBy(() -> service.preview(file, token()))
            .hasFieldOrPropertyWithValue("errorKey", "inventory.loanTemplate.invalid");
        verify(templateRepository, never()).saveAndFlush(any());
        assertThat(settings.getActiveTemplate()).isNull();
    }

    @Test
    void checksAFileWithoutSavingIt() {
        CheckResult valid = service.check(new TemplateFile("modello.html", defaults.content()), token());
        CheckResult invalid = service.check(new TemplateFile("modello.html", "{{#beni}}"), token());

        assertThat(valid.valid()).isTrue();
        assertThat(valid.issues()).isEmpty();
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.issues()).anyMatch(issue -> issue.blocking() && issue.code().startsWith("syntax."));
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsFilesOverTwoMegabytes() {
        String content = "a".repeat((int) InventoryLoanTemplateService.MAX_FILE_BYTES + 1);

        assertThatThrownBy(() -> service.check(new TemplateFile("modello.html", content), token()))
            .isInstanceOf(RequestAlertException.class)
            .hasFieldOrPropertyWithValue("errorKey", "inventory.loanTemplate.tooLarge");
    }

    @Test
    void returnsToTheDefaultTemplate() {
        settings.setActiveTemplate(template(7L, 2, InventoryLoanTemplateOrigin.CUSTOM));

        Overview overview = service.useDefault(token());

        assertThat(settings.getActiveTemplate()).isNull();
        assertThat(overview.active()).isNull();
        assertThat(overview.defaultLabel()).isEqualTo(defaults.label());
        assertThat(service.currentLabel(settings)).isEqualTo("predefinito " + defaults.label());
        assertThat(service.currentContent(settings)).isEqualTo(defaults.content());
    }

    @Test
    void reusesTheCopyOfTheDefaultWithTheSameHashForNewSheets() {
        InventoryLoanTemplate copy = template(9L, null, InventoryLoanTemplateOrigin.DEFAULT);
        when(templateRepository.findFirstByOriginAndContentHashAndDeletedFalse(InventoryLoanTemplateOrigin.DEFAULT, defaults.contentHash())).thenReturn(Optional.of(copy));

        assertThat(service.templateForIssue(settings, "admin")).isSameAs(copy);
        verify(templateRepository, never()).saveAndFlush(any());
    }

    @Test
    void copiesTheCurrentDefaultWhenNoCopyExists() {
        InventoryLoanTemplate copy = service.templateForIssue(settings, "admin");

        assertThat(copy.getOrigin()).isEqualTo(InventoryLoanTemplateOrigin.DEFAULT);
        assertThat(copy.getVersionNumber()).isNull();
        assertThat(copy.getDefaultLabel()).isEqualTo(defaults.label());
        assertThat(copy.getContent()).isEqualTo(defaults.content());
        assertThat(copy.getContentHash()).isEqualTo(defaults.contentHash());
        assertThat(InventoryLoanTemplateService.label(copy)).isEqualTo("predefinito " + defaults.label());
        verify(templateRepository).lockTenantTemplates();
    }

    @Test
    void usesTheUploadedTemplateInUseForNewSheets() {
        InventoryLoanTemplate active = template(7L, 2, InventoryLoanTemplateOrigin.CUSTOM);
        settings.setActiveTemplate(active);

        assertThat(service.templateForIssue(settings, "admin")).isSameAs(active);
        assertThat(InventoryLoanTemplateService.label(active)).isEqualTo("versione 2");
    }

    @Test
    void doesNotActivateACopyOfTheDefaultAsAVersion() {
        when(templateRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(template(9L, null, InventoryLoanTemplateOrigin.DEFAULT)));

        assertThatThrownBy(() -> service.activateVersion(9L, token()))
            .hasFieldOrPropertyWithValue("errorKey", "inventory.loanTemplate.notFound");
    }

    @Test
    void validatesTheDurationRulesAndTheirVersion() {
        assertThatThrownBy(() -> service.updateDurations(new DurationsUpdate(new Durations(30, 24, 24, 36), 0), token()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("inventory.loanSettings.invalid");
                assertThat(exception.getArgs().values()).containsEntry("problems", java.util.List.of("minAboveMax"));
            });
        assertThatThrownBy(() -> service.updateDurations(new DurationsUpdate(new Durations(6, 12, 12, 24), 3), token()))
            .hasFieldOrPropertyWithValue("errorKey", "inventory.loanSettings.versionConflict");

        Overview overview = service.updateDurations(new DurationsUpdate(new Durations(6, 12, 12, 24), 0), token());

        assertThat(overview.durations()).isEqualTo(new Durations(6, 12, 12, 24));
        assertThat(settings.getMinMonths()).isEqualTo(6);
    }

    @Test
    void keepsOnlyTheNameOfTheUploadedFile() {
        assertThat(InventoryLoanTemplateService.fileName("../../etc/modello.html")).isEqualTo("modello.html");
        assertThat(InventoryLoanTemplateService.fileName("  ")).isEqualTo("modello-foglio-affidamento.html");
    }

    private InventoryLoanTemplate template(Long id, Integer versionNumber, InventoryLoanTemplateOrigin origin) {
        InventoryLoanTemplate template = new InventoryLoanTemplate();
        template.setId(id);
        template.setVersionNumber(versionNumber);
        template.setOrigin(origin);
        template.setDefaultLabel(origin == InventoryLoanTemplateOrigin.DEFAULT ? defaults.label() : null);
        template.setFileName("modello.html");
        template.setContent(defaults.content());
        template.setContentHash(defaults.contentHash());
        return template;
    }

    private static JwtAuthenticationToken token() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
