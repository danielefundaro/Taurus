package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.RoleFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@Service
@Transactional
public class NoticesServiceImpl extends CommonServiceImpl<Notices, NoticesDTO, NoticesCriteria, NoticesMapper, NoticesRepository> implements NoticesService {

    private final UsersService usersService;

    private final KeycloakService keycloakService;

    public NoticesServiceImpl(NoticesRepository noticesRepository, NoticesMapper noticesMapper, UsersService usersService, KeycloakService keycloakService) {
        super(noticesRepository, noticesMapper, NoticesService.class, Notices.class.getSimpleName());
        this.usersService = usersService;
        this.keycloakService = keycloakService;
    }

    @Override
    public Mono<Void> addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN}).toList(), abstractAuthenticationToken)
            .then(addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken));
    }

    @Override
    public Mono<Void> addNoticesAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN}).toList(), abstractAuthenticationToken)
            .then(addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken));
    }

    @Override
    public Mono<Void> addNoticesExcludeRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_ARCHIVIST}).toList(), abstractAuthenticationToken)
            .then(addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken));
    }

    @Override
    public Mono<Void> addNoticeWholeTenant(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return addNotices(name, message, new UsersCriteria(), abstractAuthenticationToken)
            .then(addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken));
    }

    @Override
    public Mono<Void> addNoticeOnlyRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_USER}).toList(), abstractAuthenticationToken);
    }

    private Mono<Void> addNoticesByRoles(String name, String message, List<RoleEnum> roles, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersCriteria criteria = new UsersCriteria();
        RoleFilter roleFilter = new RoleFilter();
        roleFilter.setIn(roles);
        criteria.setRoles(roleFilter);

        return addNotices(name, message, criteria, abstractAuthenticationToken);
    }

    private Mono<Void> addNoticesSuperAdminsOfKeycloak(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        return Mono.fromCallable(() -> {
            try {
                List<User> users = keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN);
                return Flux.fromIterable(users).flatMap(user -> {
                    NoticesDTO notice = new NoticesDTO();
                    notice.setName(name);
                    notice.setMessage(message);
                    notice.setUserId(user.getId());

                    return super.save(notice, abstractAuthenticationToken);
                }).then();
            } catch (Exception e) {
                return Mono.empty();
            }
        }).then();
    }

    private Mono<Void> addNotices(String name, String message, UsersCriteria usersCriteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        return usersService.findEntitiesByCriteria(usersCriteria, PageRequest.of(0, Integer.MAX_VALUE), abstractAuthenticationToken)
            .flatMapMany(page -> Flux.fromIterable(page.getContent()))
            .flatMap(user -> {
                NoticesDTO notice = new NoticesDTO();
                notice.setName(name);
                notice.setMessage(message);
                notice.setUserId(user.getKeycloakId());

                return super.save(notice, abstractAuthenticationToken);
            })
            .then();
    }
}
