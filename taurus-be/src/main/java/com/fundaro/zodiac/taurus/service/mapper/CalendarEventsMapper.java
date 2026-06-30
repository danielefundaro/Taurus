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

/**
 * Mapper for the entity {@link CalendarEvents} and its DTO {@link CalendarEventsDTO}.
 */
@Mapper(componentModel = "spring")
public interface CalendarEventsMapper extends EntityOpenSearchMapper<CalendarEventsDTO, CalendarEvents> {

    CalendarEventsDTO toDto(CalendarEvents calendarEvents);

    CalendarEvents toEntity(CalendarEventsDTO dto);

    EventCostDTO toCostDto(EventCost cost);

    EventCost toCostEntity(EventCostDTO dto);

    EventUserEntryDTO toUserEntryDto(EventUserEntry entry);

    EventUserEntry toUserEntryEntity(EventUserEntryDTO dto);

    EventPresentUserDTO toPresentUserDto(EventPresentUser presentUser);

    EventPresentUser toPresentUserEntity(EventPresentUserDTO dto);
}
