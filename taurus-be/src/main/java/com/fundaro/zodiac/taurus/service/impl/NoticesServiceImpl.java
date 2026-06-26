package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.RoleFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
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
    public void addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN}).toList(), abstractAuthenticationToken);
        addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken);
    }

    @Override
    public void addNoticesAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN}).toList(), abstractAuthenticationToken);
        addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken);
    }

    @Override
    public void addNoticesExcludeRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_ARCHIVIST}).toList(), abstractAuthenticationToken);
        addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken);
    }

    @Override
    public void addNoticeWholeTenant(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNotices(name, message, new UsersCriteria(), abstractAuthenticationToken);
        addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken);
    }

    @Override
    public void addNoticeOnlyRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_USER}).toList(), abstractAuthenticationToken);
    }

    @Override
    public void readAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        ZonedDateTime now = ZonedDateTime.now();

        getRepository().findAllUnread(userId, tenantCode).forEach(notice -> {
            NoticesDTO noticeDTO = getMapper().toDto(notice);
            noticeDTO.setReadDate(now);
            this.update(noticeDTO.getId(), noticeDTO, abstractAuthenticationToken);
        });
    }

    @Override
    public long countUnread(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        return getRepository().countUnread(userId, tenantCode);
    }

    @Override
    public NoticesDTO read(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        NoticesDTO noticesDTO = findOne(id, abstractAuthenticationToken)
            .orElseThrow(() -> new com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Entity not found", "notices", "id.notFound"));

        if (noticesDTO.getReadDate() == null) {
            noticesDTO.setReadDate(ZonedDateTime.now());
            return super.update(id, noticesDTO, abstractAuthenticationToken);
        }

        return noticesDTO;
    }

    @Override
    public void deleteAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(abstractAuthenticationToken);
        getRepository().findAllByUserIdAndTenantCode(userId, tenantCode)
            .forEach(notices -> super.delete(notices.getId(), abstractAuthenticationToken));
    }

    private void addNoticesByRoles(String name, String message, List<RoleEnum> roles, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersCriteria criteria = new UsersCriteria();
        RoleFilter roleFilter = new RoleFilter();
        roleFilter.setIn(roles);
        criteria.setRoles(roleFilter);
        addNotices(name, message, criteria, abstractAuthenticationToken);
    }

    private void addNoticesSuperAdminsOfKeycloak(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).forEach(user -> {
            NoticesDTO notice = new NoticesDTO();
            notice.setName(name);
            notice.setMessage(message);
            notice.setUserId(user.getId());
            super.save(notice, abstractAuthenticationToken);
        });
    }

    private void addNotices(String name, String message, UsersCriteria usersCriteria, AbstractAuthenticationToken abstractAuthenticationToken) {
        usersService.findEntitiesByCriteria(usersCriteria, PageRequest.of(0, 1000), abstractAuthenticationToken)
            .getContent()
            .forEach(user -> {
                NoticesDTO notice = new NoticesDTO();
                notice.setName(name);
                notice.setMessage(message);
                notice.setUserId(user.getKeycloakId());
                super.save(notice, abstractAuthenticationToken);
            });
    }
}
