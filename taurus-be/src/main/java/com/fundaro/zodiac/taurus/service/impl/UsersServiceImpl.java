package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link Users}.
 */
@Service
@Transactional
public class UsersServiceImpl extends CommonOpenSearchServiceImpl<Users, UsersDTO, UsersCriteria, UsersMapper> implements UsersService {

    public final KeycloakService keycloakService;

    public UsersServiceImpl(OpenSearchService openSearchService, UsersMapper mapper, KeycloakService keycloakService) {
        super(openSearchService, mapper, UsersService.class, Users.class);
        this.keycloakService = keycloakService;
    }

    @Override
    public Mono<UsersDTO> save(UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.save(dto, abstractAuthenticationToken).map(usersDTO -> {
            User user = getMapper().toKeycloakUser(usersDTO);
            keycloakService.saveUser(user);

            return usersDTO;
        });
    }

    @Override
    public Mono<UsersDTO> update(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.update(id, dto, abstractAuthenticationToken).map(usersDTO -> {
            String userId = keycloakService.getUserIdByUsernameOrEmail(usersDTO.getEmail(), usersDTO.getEmail());
            User user = getMapper().toKeycloakUser(usersDTO);
            user.setId(userId);
            keycloakService.updateUser(user);

            return usersDTO;
        });
    }

    @Override
    public Mono<UsersDTO> partialUpdate(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return super.partialUpdate(id, dto, abstractAuthenticationToken).map(usersDTO -> {
            String userId = keycloakService.getUserIdByUsernameOrEmail(usersDTO.getEmail(), usersDTO.getEmail());
            User user = getMapper().toKeycloakUser(usersDTO);
            user.setId(userId);
            keycloakService.updateUser(user);

            return usersDTO;
        });
    }
}
