package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEventAvailability;
import com.fundaro.zodiac.taurus.domain.CalendarEventPresence;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import jakarta.persistence.criteria.Predicate;
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

    public CalendarEventsServiceImpl(
        CalendarEventsRepository repository,
        CalendarEventsMapper mapper,
        UsersRepository usersRepository,
        EventReminderProducer reminderProducer
    ) {
        super(repository, mapper, CalendarEventsService.class, CalendarEvents.class);
        this.usersRepository = usersRepository;
        this.reminderProducer = reminderProducer;
    }

    @Override
    public CalendarEventsDTO save(CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        CalendarEvents entity = getMapper().toEntity(dto);
        entity.setAvailabilities(new ArrayList<>());
        entity.setPresences(new ArrayList<>());
        return saveEntity(entity, token, true);
    }

    @Override
    public CalendarEventsDTO update(Long id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        applyDefaultEndDate(dto);
        CalendarEvents entity = findEntity(id);
        CalendarEvents values = getMapper().toEntity(dto);
        getMapper().partialUpdate(entity, dto);
        entity.getCosts().clear();
        if (values.getCosts() != null) entity.getCosts().addAll(values.getCosts());
        return saveEntity(entity, token, false);
    }

    @Override
    public CalendarEventsDTO partialUpdate(Long id, CalendarEventsDTO dto, AbstractAuthenticationToken token) {
        return update(id, dto, token);
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
            reminderProducer.scheduleIfNeeded(result, user.getKeycloakId(), token);
        } else {
            reminderProducer.cancelPending(eventId, user.getKeycloakId());
        }
        return result;
    }

    @Override
    public CalendarEventsDTO cancelAvailability(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        Users user = currentUser(token);
        event.getAvailabilities().removeIf(entry -> entry.getUser().getId().equals(user.getId()));
        CalendarEventsDTO result = saveEntity(event, token, false);
        reminderProducer.cancelPending(eventId, user.getKeycloakId());
        return result;
    }

    @Override
    public CalendarEventsDTO setPresentUsers(Long eventId, List<EventPresentUserDTO> presentUsers, AbstractAuthenticationToken token) {
        CalendarEvents event = findEntity(eventId);
        event.getPresences().clear();
        if (presentUsers != null) {
            presentUsers.forEach(dto -> {
                CalendarEventPresence presence = new CalendarEventPresence();
                presence.setUser(usersRepository.getReferenceById(dto.getIndex()));
                presence.setArrivalTime(dto.getArrivalTime());
                presence.setNote(dto.getNote());
                event.getPresences().add(presence);
            });
        }
        return saveEntity(event, token, false);
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
}
