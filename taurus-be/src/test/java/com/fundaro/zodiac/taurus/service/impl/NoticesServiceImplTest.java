package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapperImpl;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class NoticesServiceImplTest {

    @Mock NoticesRepository noticesRepository;
    @Mock NoticesMapper noticesMapper;
    @Mock TenantFeatureService tenantFeatureService;
    private NoticesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NoticesServiceImpl(noticesRepository, noticesMapper, tenantFeatureService);
    }

    @Test
    void shouldMapRequiredDefaultsForLegacyNotices() {
        Notices entity = new NoticesMapperImpl().toEntity(new NoticesDTO());

        assertThat(entity.getSource()).isEqualTo("GENERAL");
        assertThat(entity.getSeverity()).isEqualTo("INFO");
    }

    @Test
    void shouldCreateANavigableNoticeWithItsDeliveryKey() {
        when(noticesRepository.findBySourceEventKeyAndUserId("event-1", "admin-1")).thenReturn(Optional.empty());

        service.addNoticeToUser(new NotificationDelivery(
            "admin-1",
            "event-1",
            "Inventario: oggetto creato",
            "Mario ha creato un oggetto.",
            NotificationSource.INVENTORY,
            NotificationSeverity.SUCCESS,
            "/inventory",
            "actor-1",
            NotificationPreferencePolicy.CONFIGURABLE
        ));

        ArgumentCaptor<Notices> captor = ArgumentCaptor.forClass(Notices.class);
        verify(noticesRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("INVENTORY");
        assertThat(captor.getValue().getSeverity()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getSourceEventKey()).isEqualTo("event-1");
        assertThat(captor.getValue().getTargetPath()).isEqualTo("/inventory");
        assertThat(captor.getValue().getInsertBy()).isEqualTo("actor-1");
    }

    @Test
    void shouldIgnoreRepeatedDeliveryForTheSameUser() {
        when(noticesRepository.findBySourceEventKeyAndUserId("event-1", "admin-1")).thenReturn(Optional.of(new Notices()));

        service.addNoticeToUser(new NotificationDelivery(
            "admin-1",
            "event-1",
            "Titolo",
            "Messaggio",
            NotificationSource.GENERAL,
            NotificationSeverity.INFO,
            null,
            "actor-1",
            NotificationPreferencePolicy.CONFIGURABLE
        ));

        verify(noticesRepository, never()).save(any(Notices.class));
    }
    @Test
    void refusesASnoozeShorterThanTheConfiguredMinimum() {
        assertThatThrownBy(() -> service.snooze(1L, ZonedDateTime.now().plusMinutes(2), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("snooze.invalid");
        verify(noticesRepository, never()).findByIdAndUserId(any(), any());
    }

    @Test
    void refusesASnoozeBeyondTheConfiguredMaximum() {
        assertThatThrownBy(() -> service.snooze(1L, ZonedDateTime.now().plusDays(31), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("snooze.invalid");
    }

    @Test
    void refusesToSnoozeANoticeThatIsAlreadyRead() {
        Notices notice = ownedNotice();
        notice.setReadDate(ZonedDateTime.now());
        when(noticesRepository.findByIdAndUserId(1L, "user-1")).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> service.snooze(1L, ZonedDateTime.now().plusHours(1), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("snooze.read");
    }

    @Test
    void refusesToSnoozeANoticeOwnedByAnotherUser() {
        when(noticesRepository.findByIdAndUserId(1L, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.snooze(1L, ZonedDateTime.now().plusHours(1), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("id.notFound");
    }

    @Test
    void bumpsTheRevisionAndKeepsTheNoticeUnreadWhenSnoozing() {
        Notices notice = ownedNotice();
        ZonedDateTime until = ZonedDateTime.now().plusHours(1);
        when(noticesRepository.findByIdAndUserId(1L, "user-1")).thenReturn(Optional.of(notice));
        when(noticesRepository.saveAndFlush(any(Notices.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.snooze(1L, until, authentication());

        assertThat(notice.getSnoozedUntil()).isEqualTo(until);
        assertThat(notice.getSnoozeRevision()).isEqualTo(1);
        assertThat(notice.getReadDate()).isNull();
        assertThat(notice.getEditBy()).isEqualTo("user-1");
    }

    @Test
    void clearsTheSnoozeAndBumpsTheRevisionOnShowNow() {
        Notices notice = ownedNotice();
        notice.setSnoozedUntil(ZonedDateTime.now().plusHours(1));
        notice.setSnoozeRevision(2);
        when(noticesRepository.findByIdAndUserId(1L, "user-1")).thenReturn(Optional.of(notice));
        when(noticesRepository.saveAndFlush(any(Notices.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.unsnooze(1L, authentication());

        assertThat(notice.getSnoozedUntil()).isNull();
        assertThat(notice.getSnoozeRevision()).isEqualTo(3);
        assertThat(notice.getReadDate()).isNull();
    }

    private static Notices ownedNotice() {
        Notices notice = new Notices();
        notice.setId(1L);
        notice.setName("Titolo");
        notice.setUserId("user-1");
        notice.setSource(NotificationSource.INVENTORY.name());
        notice.setDeleted(false);
        return notice;
    }

    private static JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();
        return new JwtAuthenticationToken(jwt);
    }
}
