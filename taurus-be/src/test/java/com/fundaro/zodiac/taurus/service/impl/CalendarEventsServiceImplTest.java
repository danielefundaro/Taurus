package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.domain.CalendarEventAvailability;
import com.fundaro.zodiac.taurus.domain.CalendarEventPresence;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CalendarEventsServiceImplTest {

    @Test
    void changesExistingAvailabilityWithoutCreatingAnotherResponse() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventAvailability response = new CalendarEventAvailability();
        response.setId(10L);
        response.setUser(user);
        response.setAvailability(CalendarEventAvailability.Availability.AVAILABLE);
        response.setResponseDate(new Date(1L));
        event.getAvailabilities().add(response);
        RecordingReminderProducer reminderProducer = new RecordingReminderProducer();
        CalendarEventsServiceImpl service = service(event, user, reminderProducer);

        service.setAvailability(event.getId(), false, authentication());

        assertThat(event.getAvailabilities()).containsExactly(response);
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getAvailability()).isEqualTo(CalendarEventAvailability.Availability.UNAVAILABLE);
        assertThat(response.getResponseDate()).isAfter(new Date(1L));
        assertThat(reminderProducer.cancelled).isEqualTo(1);
        assertThat(reminderProducer.scheduled).isZero();

        service.setAvailability(event.getId(), true, authentication());

        assertThat(event.getAvailabilities()).containsExactly(response);
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getAvailability()).isEqualTo(CalendarEventAvailability.Availability.AVAILABLE);
        assertThat(reminderProducer.scheduled).isEqualTo(1);
    }

    @Test
    void createsAvailabilityWhenUserHasNotAnsweredYet() {
        Users user = user();
        CalendarEvents event = event();
        RecordingReminderProducer reminderProducer = new RecordingReminderProducer();
        CalendarEventsServiceImpl service = service(event, user, reminderProducer);

        service.setAvailability(event.getId(), true, authentication());

        assertThat(event.getAvailabilities()).hasSize(1);
        assertThat(event.getAvailabilities().get(0).getUser()).isSameAs(user);
        assertThat(event.getAvailabilities().get(0).getAvailability()).isEqualTo(CalendarEventAvailability.Availability.AVAILABLE);
        assertThat(reminderProducer.scheduled).isEqualTo(1);
    }

    @Test
    void cancellingAvailabilityAlsoCancelsPendingReminder() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventAvailability response = new CalendarEventAvailability();
        response.setUser(user);
        response.setAvailability(CalendarEventAvailability.Availability.AVAILABLE);
        response.setResponseDate(new Date());
        event.getAvailabilities().add(response);
        RecordingReminderProducer reminderProducer = new RecordingReminderProducer();
        CalendarEventsServiceImpl service = service(event, user, reminderProducer);

        service.cancelAvailability(event.getId(), authentication());

        assertThat(event.getAvailabilities()).isEmpty();
        assertThat(reminderProducer.cancelled).isEqualTo(1);
    }

    @Test
    void deletesExistingPresencesBeforeInsertingTheirReplacements() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventPresence existingPresence = new CalendarEventPresence();
        existingPresence.setId(10L);
        existingPresence.setUser(user);
        event.getPresences().add(existingPresence);
        AtomicBoolean flushedWithEmptyPresences = new AtomicBoolean();
        CalendarEventsServiceImpl service = service(
            event,
            user,
            new RecordingReminderProducer(),
            () -> flushedWithEmptyPresences.set(event.getPresences().isEmpty())
        );
        EventPresentUserDTO replacement = new EventPresentUserDTO();
        replacement.setIndex(user.getId());
        replacement.setArrivalTime(new Date());

        service.setPresentUsers(event.getId(), List.of(replacement), authentication());

        assertThat(flushedWithEmptyPresences).isTrue();
        assertThat(event.getPresences()).hasSize(1);
        assertThat(event.getPresences().get(0).getId()).isNull();
        assertThat(event.getPresences().get(0).getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void storesThePersonalReminderAndReschedulesOnlyForThatUser() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventAvailability response = new CalendarEventAvailability();
        response.setUser(user);
        response.setAvailability(CalendarEventAvailability.Availability.AVAILABLE);
        response.setResponseDate(new Date());
        event.getAvailabilities().add(response);
        RecordingReminderProducer reminderProducer = new RecordingReminderProducer();
        CalendarEventsServiceImpl service = service(event, user, reminderProducer);

        service.setReminderMinutes(event.getId(), 15, authentication());

        assertThat(response.getReminderMinutes()).isEqualTo(15);
        assertThat(reminderProducer.scheduled).isEqualTo(1);
        assertThat(reminderProducer.lastPersonalMinutes).isEqualTo(15);
        assertThat(service.findReminderMinutes(event.getId(), authentication())).isEqualTo(15);
    }

    @Test
    void refusesThePersonalReminderWithoutAConfirmedAvailability() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventAvailability response = new CalendarEventAvailability();
        response.setUser(user);
        response.setAvailability(CalendarEventAvailability.Availability.UNAVAILABLE);
        response.setResponseDate(new Date());
        event.getAvailabilities().add(response);
        CalendarEventsServiceImpl service = service(event, user, new RecordingReminderProducer());

        assertThatThrownBy(() -> service.setReminderMinutes(event.getId(), 15, authentication()))
            .isInstanceOf(RequestAlertException.class);
        assertThat(response.getReminderMinutes()).isNull();
    }

    @Test
    void refusesAReminderOutsideTheAllowedRange() {
        Users user = user();
        CalendarEvents event = event();
        CalendarEventsServiceImpl service = service(event, user, new RecordingReminderProducer());

        assertThatThrownBy(() -> service.setReminderMinutes(event.getId(), 2000, authentication()))
            .isInstanceOf(RequestAlertException.class);
    }

    private static CalendarEventsServiceImpl service(CalendarEvents event, Users user, EventReminderProducer reminderProducer) {
        return service(event, user, reminderProducer, () -> {});
    }

    private static CalendarEventsServiceImpl service(
        CalendarEvents event,
        Users user,
        EventReminderProducer reminderProducer,
        Runnable onFlush
    ) {
        CalendarEventsRepository eventsRepository = proxy(
            CalendarEventsRepository.class,
            (methodName, args) -> switch (methodName) {
                case "findByIdAndDeletedFalse" -> Optional.of(event);
                case "save" -> args[0];
                case "flush" -> {
                    onFlush.run();
                    yield null;
                }
                default -> null;
            }
        );
        UsersRepository usersRepository = proxy(
            UsersRepository.class,
            (methodName, args) -> switch (methodName) {
                case "findByKeycloakIdAndDeletedFalse" -> Optional.of(user);
                case "getReferenceById" -> {
                    Users referencedUser = new Users();
                    referencedUser.setId((Long) args[0]);
                    yield referencedUser;
                }
                default -> null;
            }
        );
        CalendarEventsMapper mapper = proxy(
            CalendarEventsMapper.class,
            (methodName, args) -> {
                if (!"toDto".equals(methodName) || !(args[0] instanceof CalendarEvents source)) return null;
                CalendarEventsDTO dto = new CalendarEventsDTO();
                dto.setId(source.getId());
                dto.setName(source.getName());
                dto.setStartDate(source.getStartDate());
                dto.setReminderMinutes(source.getReminderMinutes());
                return dto;
            }
        );
        return new CalendarEventsServiceImpl(eventsRepository, mapper, usersRepository, reminderProducer);
    }

    private static CalendarEvents event() {
        CalendarEvents event = new CalendarEvents();
        event.setId(2L);
        event.setName("Evento 2");
        event.setStartDate(Date.from(Instant.now().plusSeconds(7_200)));
        return event;
    }

    private static Users user() {
        Users user = new Users();
        user.setId(4L);
        user.setKeycloakId("user-1");
        return user;
    }

    private static AbstractAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (instance, method, args) -> invocation.call(method.getName(), args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String methodName, Object[] args);
    }

    private static final class RecordingReminderProducer extends EventReminderProducer {

        private int scheduled;
        private int cancelled;
        private Integer lastPersonalMinutes;

        private RecordingReminderProducer() {
            super(null, null);
        }

        @Override
        public void scheduleIfNeeded(CalendarEventsDTO event, String userId, Integer personalMinutes, AbstractAuthenticationToken token) {
            scheduled++;
            lastPersonalMinutes = personalMinutes;
        }

        @Override
        public void cancelPending(Long eventId, String userId) {
            cancelled++;
        }
    }
}
