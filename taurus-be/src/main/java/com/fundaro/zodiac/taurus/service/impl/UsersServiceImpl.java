package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.util.List;

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
        User user = getMapper().toKeycloakUser(dto);
        keycloakService.saveUser(user);

        // Set user's roles on keycloak
        String userId = keycloakService.getUserIdByUsernameOrEmail(dto.getEmail(), dto.getEmail());
        setUserRolesOnKeycloak(dto, userId);


        // Save keycloakId of the user
        dto.setKeycloakId(userId);
        return super.save(dto, abstractAuthenticationToken).onErrorContinue((a, b) -> keycloakService.deleteUser(userId));
    }

    @Override
    public Mono<UsersDTO> update(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return findOne(id, abstractAuthenticationToken).flatMap(usersDTO -> {
            updateUserOnKeycloak(dto, usersDTO);
            return super.update(id, dto, abstractAuthenticationToken);
        });
    }

    @Override
    public Mono<UsersDTO> partialUpdate(String id, UsersDTO dto, AbstractAuthenticationToken abstractAuthenticationToken) {
        return findOne(id, abstractAuthenticationToken).flatMap(usersDTO -> {
            updateUserOnKeycloak(dto, usersDTO);
            return super.partialUpdate(id, dto, abstractAuthenticationToken);
        });
    }

    @Override
    public Mono<UsersDTO> findMe(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        UsersCriteria usersCriteria = new UsersCriteria();
        StringFilter keycloakFilter = new StringFilter();
        keycloakFilter.setEquals(userId);
        usersCriteria.setKeycloakId(keycloakFilter);

        return findEntitiesByCriteria(usersCriteria, Pageable.ofSize(1), abstractAuthenticationToken).flatMap(page -> {
            if (page.getContent().isEmpty()) {
                return Mono.error(new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", Users.class.getSimpleName(), "id.notFound"));
            }

            return Mono.just(page.getContent().get(0));
        });
    }

    @Override
    protected List<Query> getQueries(UsersCriteria criteria) {
        List<Query> queries = super.getQueries(criteria);
        queries.addAll(Converter.stringFilterToQuery("lastName", criteria.getLastName()));
        queries.addAll(Converter.dateFilterToQuery("birthDate", criteria.getBirthDate()));
        queries.addAll(Converter.stringFilterToQuery("email", criteria.getEmail()));
        queries.addAll(Converter.stringFilterToQuery("roles", criteria.getRoles()));
        queries.addAll(Converter.booleanFilterToQuery("active", criteria.getActive()));
        queries.addAll(Converter.stringFilterToQuery("instruments.index", criteria.getInstrumentId()));
        queries.addAll(Converter.stringFilterToQuery("keycloakId", criteria.getKeycloakId()));

        return queries;
    }

    private void setUserRolesOnKeycloak(UsersDTO dto, String userId) {
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            List<Role> roles = keycloakService.getClientRoles();
            roles = roles.stream().filter(role -> dto.getRoles().stream().anyMatch(roleEnum -> role.getName().equalsIgnoreCase(roleEnum.toString()))).toList();

            if (!roles.isEmpty()) {
                keycloakService.saveUserRoles(userId, roles);
            }
        }
    }

    private void updateUserOnKeycloak(UsersDTO dto, UsersDTO usersDTO) {
        User user = getMapper().toKeycloakUser(dto);
        user.setId(usersDTO.getKeycloakId());
        keycloakService.updateUser(user);

        // Remove old roles, if any
        List<Role> userRoles = keycloakService.getUserRoles(user.getId());

        if (userRoles != null && !userRoles.isEmpty()) {
            userRoles = userRoles.stream().filter(role -> dto.getRoles().stream().noneMatch(roleEnum -> role.getName().equalsIgnoreCase(roleEnum.toString()))).toList();

            if (!userRoles.isEmpty()) {
                keycloakService.deleteUserRoles(user.getId(), userRoles);
            }
        }

        // Set user's roles on keycloak
        setUserRolesOnKeycloak(dto, user.getId());
    }
}
