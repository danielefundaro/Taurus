package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationOutbox;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationSeverity;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationStatus;
import com.fundaro.zodiac.taurus.repository.finance.FinanceNotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceNotificationDispatcherTest {

    @Mock FinanceNotificationOutboxRepository repository;
    @Mock FinanceNotificationRecipientService recipientService;
    @Mock NoticesService noticesService;

    private FinanceNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new FinanceNotificationDispatcher(repository, recipientService, noticesService);
    }

    @Test
    void deliversOnceToEveryDistinctRecipientAndMarksEventDelivered() {
        FinanceNotificationOutbox event = pendingEvent();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(recipientService.findRecipientIds(Set.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_TREASURER)))
            .thenReturn(Set.of("admin-1", "treasurer-1"));

        dispatcher.dispatch(1L);

        verify(noticesService).addFinanceNoticeToUser(
            "admin-1", "event-1", "Titolo", "Messaggio", "INFO", "/finance?tab=movements", "actor-1"
        );
        verify(noticesService).addFinanceNoticeToUser(
            "treasurer-1", "event-1", "Titolo", "Messaggio", "INFO", "/finance?tab=movements", "actor-1"
        );
        assertThat(event.getStatus()).isEqualTo(FinanceNotificationStatus.DELIVERED);
        assertThat(event.getDeliveredAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(repository).save(event);
    }

    @Test
    void schedulesAnExponentialRetryWithoutLosingTheEvent() {
        FinanceNotificationOutbox event = pendingEvent();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        ZonedDateTime before = ZonedDateTime.now();

        dispatcher.markFailure(1L, new IllegalStateException("Keycloak temporaneamente non disponibile"));

        assertThat(event.getStatus()).isEqualTo(FinanceNotificationStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(before.plusSeconds(50));
        assertThat(event.getLastError()).contains("IllegalStateException").doesNotContain("at com.");
        verify(repository).save(event);
    }

    private static FinanceNotificationOutbox pendingEvent() {
        FinanceNotificationOutbox event = new FinanceNotificationOutbox();
        event.setId(1L);
        event.initializeAudit("actor-1");
        event.setEventKey("event-1");
        event.setAggregateType("MOVEMENT");
        event.setOperation("MOVEMENT_CREATED");
        event.setTitle("Titolo");
        event.setMessage("Messaggio");
        event.setSeverity(FinanceNotificationSeverity.INFO);
        event.setTargetPath("/finance?tab=movements");
        event.setActorId("actor-1");
        event.setActorDisplayName("Mario Rossi");
        event.setRecipientRoles("ROLE_ADMIN,ROLE_SUPER_ADMIN,ROLE_TREASURER");
        event.setOccurredAt(ZonedDateTime.now().minusMinutes(1));
        event.setStatus(FinanceNotificationStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(ZonedDateTime.now().minusSeconds(1));
        return event;
    }
}
