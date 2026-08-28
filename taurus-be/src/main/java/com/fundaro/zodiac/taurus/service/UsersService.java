package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UserCalendarEventsCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersMeDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Optional;

/**
 * Service Interface for managing {@link Users}.
 */
public interface UsersService extends CommonOpenSearchService<Users, UsersDTO, UsersCriteria> {
    Optional<UsersDTO> findMe(AbstractAuthenticationToken abstractAuthenticationToken);

    UsersDTO partialUpdateOwn(UsersMeDTO dto, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteOwn(AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteOwnForGdpr(AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteForGdpr(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void sendSetupEmail(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    Page<CalendarEventsDTO> getUserCalendarEvents(Long id, UserCalendarEventsCriteria criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    Page<CalendarEventsDTO> getCurrentUserCalendarEvents(UserCalendarEventsCriteria userCalendarEventsCriteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);
}
