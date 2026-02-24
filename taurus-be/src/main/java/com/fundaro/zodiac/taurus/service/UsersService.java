package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link Users}.
 */
public interface UsersService extends CommonOpenSearchService<Users, UsersDTO, UsersCriteria> {
    Mono<UsersDTO> findMe(AbstractAuthenticationToken abstractAuthenticationToken);
}
