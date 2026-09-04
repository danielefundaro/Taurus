package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapperImpl;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
            "actor-1"
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
            "actor-1"
        ));

        verify(noticesRepository, never()).save(any(Notices.class));
    }
}
