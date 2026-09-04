package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.projection.CalendarAttentionProjection;
import com.fundaro.zodiac.taurus.repository.projection.CalendarResponseProjection;
import com.fundaro.zodiac.taurus.repository.projection.UserRoleProjection;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarOperationProvider implements DashboardOperationProvider {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.ITALIAN);
    private final CalendarEventsRepository eventsRepository;
    private final UsersRepository usersRepository;
    private final ApplicationProperties.DashboardProperties properties;

    public CalendarOperationProvider(
        CalendarEventsRepository eventsRepository,
        UsersRepository usersRepository,
        ApplicationProperties applicationProperties
    ) {
        this.eventsRepository = eventsRepository;
        this.usersRepository = usersRepository;
        this.properties = applicationProperties.getDashboard();
    }

    @Override
    public DashboardDomain domain() {
        return DashboardDomain.CALENDAR;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
        List<OperationalItemDTO> result = new ArrayList<>();
        ZonedDateTime limit = context.generatedAt().plusDays(properties.getCalendarLookAheadDays());
        personalOperation(context, limit).ifPresent(result::add);
        administrativeOperation(context, limit).ifPresent(result::add);
        return result;
    }

    private java.util.Optional<OperationalItemDTO> personalOperation(DashboardRequestContext context, ZonedDateTime limit) {
        boolean internalParticipant = context.hasAnyAuthority(
            AuthoritiesConstants.SUPER_ADMIN,
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.ARCHIVIST,
            AuthoritiesConstants.USER
        );
        boolean externalParticipant = context.hasAnyAuthority(AuthoritiesConstants.USER_EXTERNAL);
        if (!internalParticipant && !externalParticipant) return java.util.Optional.empty();

        Set<StateEnum> visibleStates = internalParticipant
            ? EnumSet.of(StateEnum.COMPLETE, StateEnum.PUBLIC)
            : EnumSet.of(StateEnum.PUBLIC);
        CalendarAttentionProjection summary = eventsRepository.summarizeMissingAvailability(
            context.subject(),
            visibleStates,
            Date.from(context.generatedAt().toInstant()),
            Date.from(limit.toInstant())
        );
        if (summary == null || summary.getEventCount() == 0 || summary.getEarliestStartDate() == null) {
            return java.util.Optional.empty();
        }
        ZonedDateTime dueAt = summary.getEarliestStartDate().toInstant().atZone(context.zoneId());
        DashboardSeverity severity = isWithinTwentyFourHours(context.generatedAt(), dueAt)
            ? DashboardSeverity.DANGER
            : DashboardSeverity.WARNING;
        String description = "Il primo evento inizia " + DATE_TIME.format(dueAt) + ".";
        return java.util.Optional.of(item(
            DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED,
            severity,
            summary.getEventCount(),
            null,
            "Disponibilità da indicare",
            description,
            dueAt,
            "Rispondi",
            "/calendar?attention=my-missing-availability"
        ));
    }

    private java.util.Optional<OperationalItemDTO> administrativeOperation(DashboardRequestContext context, ZonedDateTime limit) {
        if (!context.hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)) {
            return java.util.Optional.empty();
        }
        List<UserRoleProjection> audience = usersRepository.findActiveUserRoles(List.of(RoleEnum.ROLE_USER, RoleEnum.ROLE_USER_EXTERNAL));
        Set<String> internal = new HashSet<>();
        Set<String> external = new HashSet<>();
        for (UserRoleProjection entry : audience) {
            if (entry.getRole() == RoleEnum.ROLE_USER) internal.add(entry.getKeycloakId());
            if (entry.getRole() == RoleEnum.ROLE_USER_EXTERNAL) external.add(entry.getKeycloakId());
        }
        Set<String> publicAudience = new HashSet<>(internal);
        publicAudience.addAll(external);

        List<CalendarResponseProjection> summaries = new ArrayList<>();
        Date from = Date.from(context.generatedAt().toInstant());
        Date to = Date.from(limit.toInstant());
        if (!internal.isEmpty()) summaries.addAll(eventsRepository.summarizeResponses(StateEnum.COMPLETE, internal, from, to));
        if (!publicAudience.isEmpty()) summaries.addAll(eventsRepository.summarizeResponses(StateEnum.PUBLIC, publicAudience, from, to));

        long eventCount = 0;
        long missingResponses = 0;
        CalendarResponseProjection nearest = null;
        for (CalendarResponseProjection summary : summaries) {
            long expected = summary.getState() == StateEnum.COMPLETE ? internal.size() : publicAudience.size();
            long missing = Math.max(0, expected - summary.getResponseCount());
            if (missing == 0) continue;
            eventCount++;
            missingResponses += missing;
            if (nearest == null || summary.getStartDate().before(nearest.getStartDate())) nearest = summary;
        }
        if (eventCount == 0 || nearest == null) return java.util.Optional.empty();
        ZonedDateTime dueAt = nearest.getStartDate().toInstant().atZone(context.zoneId());
        DashboardSeverity severity = isWithinTwentyFourHours(context.generatedAt(), dueAt)
            ? DashboardSeverity.DANGER
            : DashboardSeverity.WARNING;
        String description = missingResponses + (missingResponses == 1 ? " risposta mancante" : " risposte mancanti")
            + "; il primo evento è “" + nearest.getEventName() + "”.";
        return java.util.Optional.of(item(
            DashboardOperationType.CALENDAR_RESPONSES_MISSING,
            severity,
            eventCount,
            missingResponses,
            "Risposte agli eventi mancanti",
            description,
            dueAt,
            "Verifica risposte",
            "/calendar?attention=missing-availability"
        ));
    }

    private static boolean isWithinTwentyFourHours(ZonedDateTime now, ZonedDateTime dueAt) {
        Duration remaining = Duration.between(now.toInstant(), dueAt.toInstant());
        return !remaining.isNegative() && remaining.compareTo(Duration.ofHours(24)) <= 0;
    }

    private static OperationalItemDTO item(
        DashboardOperationType type,
        DashboardSeverity severity,
        long count,
        Long relatedCount,
        String title,
        String description,
        ZonedDateTime dueAt,
        String actionLabel,
        String targetPath
    ) {
        return new OperationalItemDTO(type.name(), type, DashboardDomain.CALENDAR, severity, count, relatedCount, title, description, dueAt, actionLabel, targetPath);
    }
}
