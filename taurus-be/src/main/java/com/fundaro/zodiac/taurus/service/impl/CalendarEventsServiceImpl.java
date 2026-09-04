package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEventAvailability;
import com.fundaro.zodiac.taurus.domain.CalendarEventPresence;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.dto.BulkAvailabilityResultDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CalendarEventsServiceImpl
    extends CommonOpenSearchServiceImpl<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsMapper, CalendarEventsRepository>
    implements CalendarEventsService {

    private final UsersRepository usersRepository;
    private final EventReminderProducer reminderProducer;
    private final TenantFeatureService tenantFeatureService;

    public CalendarEventsServiceImpl(
        CalendarEventsRepository repository,
        CalendarEventsMapper mapper,
        UsersRepository usersRepository,
        EventReminderProducer reminderProducer,
        TenantFeatureService tenantFeatureService
    ) {
        super(repository, mapper, CalendarEventsService.class, CalendarEvents.class);
        this.usersRepository = usersRepository;
        this.reminderProducer = reminderProducer;
        this.tenantFeatureService = tenantFeatureService;
    }

    @Override
    public CalendarEventsDTO save(CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        if (!financeEnabled()) {
            dto.setFee(null);
            dto.setCosts(List.of());
        }
        CalendarEvents entity = getMapper().toEntity(dto);
        entity.setAvailabilities(new ArrayList<>());
        entity.setPresences(new ArrayList<>());
        return featureSafe(saveEntity(entity, token, true));
    }

    @Override
    public CalendarEventsDTO update(Long id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        CalendarEvents entity = findEntity(id);
        boolean financeEnabled = financeEnabled();
        java.math.BigDecimal persistedFee = entity.getFee();
        CalendarEvents values = getMapper().toEntity(dto);
        getMapper().partialUpdate(entity, dto);
        if (!financeEnabled) entity.setFee(persistedFee);
        if (entity.getSeries() != null) entity.setSeriesException(true);
        if (financeEnabled) {
            entity.getCosts().clear();
            if (values.getCosts() != null) entity.getCosts().addAll(values.getCosts());
        }
        CalendarEventsDTO result = featureSafe(saveEntity(entity, token, false));
        reminderProducer.rescheduleForAvailableUsers(result, availableUsers(entity), token);
        return result;
    }

    @Override
    public CalendarEventsDTO partialUpdate(Long id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        return update(id, dto, token);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CalendarEventsDTO> findOne(Long id, AbstractAuthenticationToken token) {
        return super.findOne(id, token).map(this::featureSafe);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CalendarEventsDTO> findEntitiesByCriteria(
        CalendarEventsCriteria criteria,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        return super.findEntitiesByCriteria(criteria, pageable, token).map(this::featureSafe);
    }

    @Override
    protected Specification<CalendarEvents> buildSpecification(CalendarEventsCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            addRangeFilter(predicates, cb, root.get("startDate"), criteria.getStartDate());
            addRangeFilter(predicates, cb, root.get("endDate"), criteria.getEndDate());
            addStringFilter(predicates, cb, root.get("location"), criteria.getLocation());
            addFilter(predicates, cb, root.get("state"), criteria.getState());
            if (criteria.getPresentUserId() != null) {
                query.distinct(true);
                addFilter(predicates, cb, root.join("presences").join("user").get("id"), criteria.getPresentUserId());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public CalendarEventsDTO setAvailability(Long eventId, boolean available, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        Users user = currentUser(token);
        CalendarEventAvailability response = event.getAvailabilities().stream()
            .filter(entry -> entry.getUser().getId().equals(user.getId()))
            .findFirst()
            .orElseGet(() -> {
                CalendarEventAvailability newResponse = new CalendarEventAvailability();
                newResponse.setUser(user);
                event.getAvailabilities().add(newResponse);
                return newResponse;
            });
        response.setAvailability(available
            ? CalendarEventAvailability.Availability.AVAILABLE
            : CalendarEventAvailability.Availability.UNAVAILABLE);
        response.setResponseDate(new Date());
        CalendarEventsDTO result = saveEntity(event, token, false);
        if (available) {
            reminderProducer.scheduleIfNeeded(result, user.getKeycloakId(), response.getReminderMinutes(), token);
        } else {
            reminderProducer.cancelPending(eventId, user.getKeycloakId());
        }
        return featureSafe(result);
    }

    @Override
    public CalendarEventsDTO cancelAvailability(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        Users user = currentUser(token);
        event.getAvailabilities().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
        CalendarEventsDTO result = saveEntity(event, token, false);
        reminderProducer.cancelPending(eventId, user.getKeycloakId());
        return featureSafe(result);
    }

    @Override
    public CalendarEventsDTO setReminderMinutes(Long eventId, Integer minutes, AbstractAuthenticationToken token) {
        if (minutes != null && (minutes < 0 || minutes > 1440)) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Reminder must be between 0 and 1440 minutes", getEntityName(), "reminder.invalid");
        }
        CalendarEvents event = findEntity(eventId);
        Users user = currentUser(token);
        CalendarEventAvailability response = event.getAvailabilities().stream()
            .filter(entry -> entry.getUser().getId().equals(user.getId()))
            .filter(entry -> entry.getAvailability() == CalendarEventAvailability.Availability.AVAILABLE)
            .findFirst()
            .orElseThrow(() ->
                new RequestAlertException(HttpStatus.CONFLICT, "Availability must be confirmed before setting a reminder", getEntityName(), "reminder.availabilityMissing")
            );
        response.setReminderMinutes(minutes);
        CalendarEventsDTO result = saveEntity(event, token, false);
        reminderProducer.scheduleIfNeeded(result, user.getKeycloakId(), minutes, token);
        return featureSafe(result);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer findReminderMinutes(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        Users user = currentUser(token);
        return event.getAvailabilities().stream()
            .filter(entry -> entry.getUser().getId().equals(user.getId()))
            .findFirst()
            .map(CalendarEventAvailability::getReminderMinutes)
            .orElse(null);
    }

    @Override
    public CalendarEventsDTO setPresentUsers(Long eventId, List<EventPresentUserDTO> presentUsers, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        event.getPresences().clear();
        // Flush orphan removals before inserting the replacement rows. Otherwise
        // Hibernate can insert first and violate the unique (event_id, user_id) constraint.
        getRepository().flush();
        if (presentUsers != null) {
            presentUsers.forEach(dto -> {
                CalendarEventPresence presence = new CalendarEventPresence();
                presence.setUser(usersRepository.getReferenceById(dto.getIndex()));
                presence.setArrivalTime(dto.getArrivalTime());
                presence.setNote(dto.getNote());
                event.getPresences().add(presence);
            });
        }
        return featureSafe(saveEntity(event, token, false));
    }

    @Override
    public BulkAvailabilityResultDTO setSeriesAvailability(
        Long seriesId,
        Boolean available,
        List<StateEnum> visibleStates,
        AbstractAuthenticationToken token
    ) {
        Users user = currentUser(token);
        Date now = new Date();
        List<CalendarEvents> events = getRepository().findAllBySeries_IdOrderByOriginalStartDateAsc(seriesId).stream()
            .filter(event -> !event.getDeleted())
            .filter(event -> event.getStartDate() != null && !event.getStartDate().before(now))
            .filter(event -> visibleStates == null || visibleStates.contains(event.getState()))
            .toList();
        for (CalendarEvents event : events) {
            if (available == null) {
                event.getAvailabilities().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
                reminderProducer.cancelPending(event.getId(), user.getKeycloakId());
                continue;
            }
            CalendarEventAvailability response = event.getAvailabilities().stream()
                .filter(entry -> entry.getUser().getId().equals(user.getId()))
                .findFirst()
                .orElseGet(() -> {
                    CalendarEventAvailability newResponse = new CalendarEventAvailability();
                    newResponse.setUser(user);
                    event.getAvailabilities().add(newResponse);
                    return newResponse;
                });
            response.setAvailability(available
                ? CalendarEventAvailability.Availability.AVAILABLE
                : CalendarEventAvailability.Availability.UNAVAILABLE);
            response.setResponseDate(now);
        }
        getRepository().saveAll(events);
        getRepository().flush();
        if (available != null && available) {
            events.forEach(event ->
                reminderProducer.scheduleIfNeeded(
                    getMapper().toDto(event),
                    user.getKeycloakId(),
                    event.getAvailabilities().stream()
                        .filter(entry -> entry.getUser().getId().equals(user.getId()))
                        .findFirst()
                        .map(CalendarEventAvailability::getReminderMinutes)
                        .orElse(null),
                    token
                )
            );
        } else {
            events.forEach(event -> reminderProducer.cancelPending(event.getId(), user.getKeycloakId()));
        }
        return new BulkAvailabilityResultDTO(seriesId, events.size());
    }

    @Override
    public CalendarEventsDTO delete(Long id, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(id);
        if (event.getSeries() != null) event.setSeriesExcluded(true);
        event.setDeleted(true);
        event.setEditBy(SecurityUtils.getUserIdFromAuthentication(token));
        event.setEditDate(new Date());
        CalendarEventsDTO result = getMapper().toDto(getRepository().save(event));
        reminderProducer.cancelAllPending(id);
        return featureSafe(result);
    }

    private CalendarEvents findEntity(Long id) {
        return getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
    }

    private Users currentUser(AbstractAuthenticationToken token) {
        return usersRepository.findByKeycloakIdAndDeletedFalse(SecurityUtils.getUserIdFromAuthentication(token))
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Current user not found", "Users", "user.notFound"));
    }

    private void applyDefaultEndDate(CalendarEventsDTO dto) {
        if (dto.getEndDate() == null && dto.getStartDate() != null) {
            dto.setEndDate(new Date(dto.getStartDate().getTime() + 3_600_000L));
        }
    }

    private Map<String, Integer> availableUsers(CalendarEvents event) {
        Map<String, Integer> result = new LinkedHashMap<>();
        event.getAvailabilities().stream()
            .filter(value -> value.getAvailability() == CalendarEventAvailability.Availability.AVAILABLE)
            .forEach(value -> result.put(value.getUser().getKeycloakId(), value.getReminderMinutes()));
        return result;
    }

    private boolean financeEnabled() {
        return tenantFeatureService.isEnabled(TenantFeature.FINANCE);
    }

    private CalendarEventsDTO featureSafe(CalendarEventsDTO dto) {
        if (!financeEnabled()) {
            dto.setFee(null);
            dto.setCosts(List.of());
        }
        return dto;
    }
}
