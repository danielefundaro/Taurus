package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.EventPresentUser;
import com.fundaro.zodiac.taurus.domain.EventUserEntry;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventCostDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.dto.EventUserEntryDTO;
import org.mapstruct.Mapper;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import java.util.ArrayList;

/**
 * Mapper for the entity {@link CalendarEvents} and its DTO {@link CalendarEventsDTO}.
 */
@Mapper(componentModel = "spring")
public interface CalendarEventsMapper extends EntityOpenSearchMapper<CalendarEventsDTO, CalendarEvents> {

    @Mapping(target = "availableUsers", ignore = true)
    @Mapping(target = "unavailableUsers", ignore = true)
    @Mapping(target = "presentUsers", ignore = true)
    @Mapping(target = "seriesId", source = "series.id")
    CalendarEventsDTO toDto(CalendarEvents calendarEvents);

    @Mapping(target = "availabilities", ignore = true)
    @Mapping(target = "presences", ignore = true)
    @Mapping(target = "availableUsers", ignore = true)
    @Mapping(target = "unavailableUsers", ignore = true)
    @Mapping(target = "presentUsers", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "originalStartDate", ignore = true)
    @Mapping(target = "seriesSequence", ignore = true)
    @Mapping(target = "seriesException", ignore = true)
    @Mapping(target = "seriesExcluded", ignore = true)
    CalendarEvents toEntity(CalendarEventsDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "costs", ignore = true)
    @Mapping(target = "availabilities", ignore = true)
    @Mapping(target = "presences", ignore = true)
    @Mapping(target = "availableUsers", ignore = true)
    @Mapping(target = "unavailableUsers", ignore = true)
    @Mapping(target = "presentUsers", ignore = true)
    @Mapping(target = "series", ignore = true)
    @Mapping(target = "originalStartDate", ignore = true)
    @Mapping(target = "seriesSequence", ignore = true)
    @Mapping(target = "seriesException", ignore = true)
    @Mapping(target = "seriesExcluded", ignore = true)
    void partialUpdate(@MappingTarget CalendarEvents entity, CalendarEventsDTO dto);

    EventCostDTO toCostDto(EventCost cost);

    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "insertBy", ignore = true)
    @Mapping(target = "insertDate", ignore = true)
    @Mapping(target = "editBy", ignore = true)
    @Mapping(target = "editDate", ignore = true)
    EventCost toCostEntity(EventCostDTO dto);

    EventUserEntryDTO toUserEntryDto(EventUserEntry entry);

    EventUserEntry toUserEntryEntity(EventUserEntryDTO dto);

    EventPresentUserDTO toPresentUserDto(EventPresentUser presentUser);

    EventPresentUser toPresentUserEntity(EventPresentUserDTO dto);

    @AfterMapping
    default void addRelationalUsers(CalendarEvents entity, @MappingTarget CalendarEventsDTO dto) {
        dto.setAvailableUsers(new ArrayList<>());
        dto.setUnavailableUsers(new ArrayList<>());
        if (entity.getAvailabilities() != null) {
            entity.getAvailabilities().forEach(availability -> {
                EventUserEntryDTO entry = new EventUserEntryDTO();
                entry.setIndex(availability.getUser().getId());
                entry.setName(availability.getUser().getName());
                entry.setLastName(availability.getUser().getLastName());
                entry.setResponseDate(availability.getResponseDate());
                if (availability.getAvailability() == com.fundaro.zodiac.taurus.domain.CalendarEventAvailability.Availability.AVAILABLE) {
                    dto.getAvailableUsers().add(entry);
                } else {
                    dto.getUnavailableUsers().add(entry);
                }
            });
        }
        dto.setPresentUsers(new ArrayList<>());
        if (entity.getPresences() != null) {
            entity.getPresences().forEach(presence -> {
                EventPresentUserDTO entry = new EventPresentUserDTO();
                entry.setIndex(presence.getUser().getId());
                entry.setName(presence.getUser().getName());
                entry.setLastName(presence.getUser().getLastName());
                entry.setArrivalTime(presence.getArrivalTime());
                entry.setNote(presence.getNote());
                dto.getPresentUsers().add(entry);
            });
        }
    }
}
