package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.notification.NotificationCategoryPreference;
import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationCategoryPreferenceDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationQuietHoursDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;

@Service
@Transactional
public class NotificationPreferencesService {

    private final NotificationProfileRepository repository;
    private final UsersRepository usersRepository;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final CalendarEventsRepository calendarEventsRepository;
    private final CalendarEventsMapper calendarEventsMapper;
    private final EventReminderProducer eventReminderProducer;
    private final ApplicationProperties.NotificationPreferencesProperties properties;
    private NotificationPreferenceMetrics metrics;

    public NotificationPreferencesService(
        NotificationProfileRepository repository,
        UsersRepository usersRepository,
        TenantTimeZoneService tenantTimeZoneService,
        CalendarEventsRepository calendarEventsRepository,
        CalendarEventsMapper calendarEventsMapper,
        EventReminderProducer eventReminderProducer,
        ApplicationProperties applicationProperties
    ) {
        this.repository = repository;
        this.usersRepository = usersRepository;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.calendarEventsRepository = calendarEventsRepository;
        this.calendarEventsMapper = calendarEventsMapper;
        this.eventReminderProducer = eventReminderProducer;
        this.properties = applicationProperties.getNotificationPreferences();
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setMetrics(NotificationPreferenceMetrics metrics) {
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesDTO get(AbstractAuthenticationToken authentication) {
        requireEnabled();
        String subject = subject(authentication);
        return repository.findByUserKeycloakIdAndDeletedFalse(subject)
            .map(this::toDto)
            .orElseGet(() -> defaults(tenantTimeZoneService.currentZoneId()));
    }

    public NotificationPreferencesDTO save(NotificationPreferencesDTO request, AbstractAuthenticationToken authentication) {
        requireEnabled();
        String subject = subject(authentication);
        validate(request);
        Users user = usersRepository.findByKeycloakIdAndDeletedFalse(subject)
            .filter(value -> Boolean.TRUE.equals(value.getActive()))
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Authenticated user is not active", "user.notFound"));

        NotificationProfile profile = repository.findByUserKeycloakIdAndDeletedFalse(subject).orElse(null);
        boolean reminderScheduleChanged = profile == null ||
            profile.isEventRemindersEnabled() != request.eventRemindersEnabled() ||
            profile.getDefaultCalendarReminderMinutes() != request.defaultCalendarReminderMinutes();
        if (profile == null) {
            if (request.version() != null) throw versionConflict();
            profile = new NotificationProfile();
            profile.initializeAudit(subject);
            profile.setUser(user);
        } else {
            if (request.version() == null || request.version() != profile.getEntityVersion()) throw versionConflict();
            profile.touchAudit(subject);
        }

        profile.setTimeZone(ZoneId.of(request.timeZone()).getId());
        profile.setEventRemindersEnabled(request.eventRemindersEnabled());
        profile.setDefaultCalendarReminderMinutes(request.defaultCalendarReminderMinutes());
        profile.setQuietHoursEnabled(request.quietHours().enabled());
        profile.setQuietStart(request.quietHours().start());
        profile.setQuietEnd(request.quietHours().end());
        profile.setPushPausedUntil(request.pushPausedUntil());
        profile.setDigestLocalTime(request.digestLocalTime());
        profile.setPushPreview(request.pushPreview());
        synchronizeCategories(profile, request.categories(), subject);
        NotificationProfile saved = repository.saveAndFlush(profile);
        if (reminderScheduleChanged) rescheduleFutureReminders(subject, authentication);
        return toDto(saved);
    }

    private void rescheduleFutureReminders(String subject, AbstractAuthenticationToken authentication) {
        calendarEventsRepository.findFutureAvailableForUser(
            subject,
            new java.util.Date(),
            com.fundaro.zodiac.taurus.domain.CalendarEventAvailability.Availability.AVAILABLE
        ).forEach(event -> {
            Integer personalMinutes = event.getAvailabilities().stream()
                .filter(value -> value.getUser().getKeycloakId().equals(subject))
                .findFirst()
                .map(value -> value.getReminderMinutes())
                .orElse(null);
            eventReminderProducer.scheduleIfNeeded(calendarEventsMapper.toDto(event), subject, personalMinutes, authentication);
        });
    }

    private void synchronizeCategories(
        NotificationProfile profile,
        List<NotificationCategoryPreferenceDTO> requested,
        String actor
    ) {
        Map<NotificationSource, NotificationCategoryPreference> existing = profile.getCategories().stream()
            .collect(Collectors.toMap(NotificationCategoryPreference::getSource, value -> value));
        List<NotificationCategoryPreference> ordered = new ArrayList<>();
        for (NotificationCategoryPreferenceDTO value : requested) {
            NotificationCategoryPreference category = existing.get(value.source());
            if (category == null) {
                category = new NotificationCategoryPreference();
                category.initializeAudit(actor);
                category.setProfile(profile);
            } else {
                category.touchAudit(actor);
            }
            category.setSource(value.source());
            category.setInAppEnabled(value.inAppEnabled());
            category.setPushMode(value.pushMode());
            ordered.add(category);
        }
        profile.getCategories().clear();
        profile.getCategories().addAll(ordered);
    }

    private void validate(NotificationPreferencesDTO request) {
        if (request == null) throw error(HttpStatus.BAD_REQUEST, "Notification preferences are required", "preferences.required");
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException | NullPointerException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid time zone", "timeZone.invalid");
        }
        if (request.defaultCalendarReminderMinutes() < 0 || request.defaultCalendarReminderMinutes() > 1440) {
            throw error(HttpStatus.BAD_REQUEST, "Reminder minutes must be between 0 and 1440", "reminder.invalid");
        }
        NotificationQuietHoursDTO quiet = request.quietHours();
        if (quiet == null || quiet.start() == null || quiet.end() == null || (quiet.enabled() && quiet.start().equals(quiet.end()))) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid quiet hours", "quietHours.invalid");
        }
        if (request.digestLocalTime() == null || request.pushPreview() == null) {
            throw error(HttpStatus.BAD_REQUEST, "Digest time and push preview are required", "preferences.invalid");
        }
        Set<NotificationSource> sources = EnumSet.noneOf(NotificationSource.class);
        if (request.categories() == null || request.categories().size() != NotificationSource.values().length) {
            throw error(HttpStatus.BAD_REQUEST, "Every notification category is required", "categories.incomplete");
        }
        for (NotificationCategoryPreferenceDTO category : request.categories()) {
            if (category == null || category.source() == null || category.pushMode() == null || !sources.add(category.source())) {
                throw error(HttpStatus.BAD_REQUEST, "Notification categories must be known and unique", "categories.invalid");
            }
        }
        if (!sources.equals(EnumSet.allOf(NotificationSource.class))) {
            throw error(HttpStatus.BAD_REQUEST, "Every notification category is required", "categories.incomplete");
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (request.pushPausedUntil() != null &&
            (request.pushPausedUntil().isBefore(now) || request.pushPausedUntil().isAfter(now.plusDays(properties.getMaxPauseDays())))) {
            throw error(HttpStatus.BAD_REQUEST, "Push pause must be in the next 30 days", "pushPause.invalid");
        }
        boolean digestEnabled = request.categories().stream().anyMatch(value -> value.pushMode() == NotificationPushMode.DAILY_DIGEST);
        if (digestEnabled && quiet.enabled() && isWithin(quiet.start(), quiet.end(), request.digestLocalTime())) {
            throw error(HttpStatus.BAD_REQUEST, "Digest time cannot be inside quiet hours", "digest.quietHours");
        }
    }

    static boolean isWithin(LocalTime start, LocalTime end, LocalTime value) {
        if (start.isBefore(end)) return !value.isBefore(start) && value.isBefore(end);
        return !value.isBefore(start) || value.isBefore(end);
    }

    private NotificationPreferencesDTO defaults(ZoneId zoneId) {
        List<NotificationCategoryPreferenceDTO> categories = new ArrayList<>();
        for (NotificationSource source : NotificationSource.values()) {
            categories.add(new NotificationCategoryPreferenceDTO(source, true, NotificationPushMode.OFF));
        }
        return new NotificationPreferencesDTO(
            null,
            zoneId.getId(),
            true,
            properties.getDefaultCalendarReminderMinutes(),
            new NotificationQuietHoursDTO(false, LocalTime.of(22, 0), LocalTime.of(7, 0)),
            null,
            LocalTime.parse(properties.getDefaultDigestLocalTime()),
            NotificationPushPreview.PRIVATE,
            List.copyOf(categories)
        );
    }

    private NotificationPreferencesDTO toDto(NotificationProfile profile) {
        Map<NotificationSource, NotificationCategoryPreference> bySource = new EnumMap<>(NotificationSource.class);
        profile.getCategories().forEach(value -> bySource.put(value.getSource(), value));
        List<NotificationCategoryPreferenceDTO> categories = new ArrayList<>();
        for (NotificationSource source : NotificationSource.values()) {
            NotificationCategoryPreference value = bySource.get(source);
            categories.add(value == null
                ? new NotificationCategoryPreferenceDTO(source, true, NotificationPushMode.OFF)
                : new NotificationCategoryPreferenceDTO(source, value.isInAppEnabled(), value.getPushMode()));
        }
        return new NotificationPreferencesDTO(
            profile.getEntityVersion(),
            profile.getTimeZone(),
            profile.isEventRemindersEnabled(),
            profile.getDefaultCalendarReminderMinutes(),
            new NotificationQuietHoursDTO(profile.isQuietHoursEnabled(), profile.getQuietStart(), profile.getQuietEnd()),
            profile.getPushPausedUntil(),
            profile.getDigestLocalTime(),
            profile.getPushPreview(),
            List.copyOf(categories)
        );
    }

    /** Il profilo notifiche resta dietro feature flag finché il rollout non è completo. */
    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw error(HttpStatus.NOT_FOUND, "Notification preferences are not enabled", "preferences.disabled");
        }
    }

    private static String subject(AbstractAuthenticationToken authentication) {
        String subject = SecurityUtils.getUserIdFromAuthentication(authentication);
        if (subject == null || subject.isBlank()) throw error(HttpStatus.UNAUTHORIZED, "Authentication subject is required", "subject.missing");
        return subject;
    }

    private RequestAlertException versionConflict() {
        if (metrics != null) metrics.recordProfileConflict();
        return conflict();
    }

    private static RequestAlertException conflict() {
        return error(HttpStatus.CONFLICT, "Notification preferences were changed by another request", "preferences.versionConflict");
    }

    private static RequestAlertException error(HttpStatus status, String message, String code) {
        return new RequestAlertException(status, message, "notificationPreferences", code);
    }
}
