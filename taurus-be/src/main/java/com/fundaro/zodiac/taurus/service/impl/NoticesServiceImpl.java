package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.UsersCriteria;
import com.fundaro.zodiac.taurus.domain.criteria.filter.RoleFilter;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationOutbox;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinanceNotificationOutboxRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.NoticesService.FinanceNoticeCommand;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@Service
@Transactional
public class NoticesServiceImpl extends CommonServiceImpl<Notices, NoticesDTO, NoticesCriteria, NoticesMapper, NoticesRepository> implements NoticesService {

    private static final int MAX_NOTICE_TEXT = 255;

    private final UsersService usersService;

    private final KeycloakService keycloakService;

    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final FinanceNotificationOutboxRepository financeNotificationOutboxRepository;

    public NoticesServiceImpl(
        NoticesRepository noticesRepository,
        NoticesMapper noticesMapper,
        UsersService usersService,
        KeycloakService keycloakService,
        TenantTransactionExecutor tenantTransactionExecutor,
        FinanceNotificationOutboxRepository financeNotificationOutboxRepository
    ) {
        super(noticesRepository, noticesMapper, NoticesService.class, Notices.class.getSimpleName());
        this.usersService = usersService;
        this.keycloakService = keycloakService;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.financeNotificationOutboxRepository = financeNotificationOutboxRepository;
    }

    @Override
    public void addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_SUPER_ADMIN}).toList(), abstractAuthenticationToken);
        addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken);
    }

    @Override
    public void addNoticesSuperAdminsForTenant(
        String tenantCode,
        String name,
        String message,
        AbstractAuthenticationToken abstractAuthenticationToken
    ) {
        tenantTransactionExecutor.execute(
            tenantCode,
            () -> addNoticesSuperAdminsOfKeycloak(name, message, abstractAuthenticationToken)
        );
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
        addNoticesByRoles(name, message, Arrays.stream(new RoleEnum[]{RoleEnum.ROLE_USER, RoleEnum.ROLE_USER_EXTERNAL}).toList(), abstractAuthenticationToken);
    }

    @Override
    public void addNoticeToUser(String userId, String name, String message, AbstractAuthenticationToken abstractAuthenticationToken) {
        NoticesDTO notice = new NoticesDTO();
        notice.setName(name);
        notice.setMessage(message);
        notice.setUserId(userId);
        super.save(notice, abstractAuthenticationToken);
    }

    @Override
    public void addNoticeToUser(String userId, String name, String message, String actor) {
        ZonedDateTime now = ZonedDateTime.now();
        Notices notice = new Notices();
        notice.setName(name);
        notice.setMessage(message);
        notice.setUserId(userId);
        notice.setDeleted(false);
        notice.setInsertBy(actor);
        notice.setInsertDate(now);
        notice.setEditBy(actor);
        notice.setEditDate(now);
        getRepository().save(notice);
    }

    @Override
    public void addFinanceNoticeToUser(
        String userId,
        String eventKey,
        String name,
        String message,
        String severity,
        String targetPath,
        String actor
    ) {
        if (getRepository().findBySourceEventKeyAndUserId(eventKey, userId).isPresent()) return;
        ZonedDateTime now = ZonedDateTime.now();
        Notices notice = new Notices();
        notice.setName(name);
        notice.setMessage(message);
        notice.setUserId(userId);
        notice.setSource("FINANCE");
        notice.setSeverity(severity);
        notice.setTargetPath(targetPath);
        notice.setSourceEventKey(eventKey);
        notice.setDeleted(false);
        notice.setInsertBy(actor);
        notice.setInsertDate(now);
        notice.setEditBy(actor);
        notice.setEditDate(now);
        getRepository().save(notice);
    }

    @Override
    public void enqueueFinanceNotice(FinanceNoticeCommand notice) {
        ZonedDateTime now = ZonedDateTime.now();
        FinanceNotificationOutbox event = new FinanceNotificationOutbox();
        event.initializeAudit(notice.actorId());
        event.setEventKey(value(notice.eventKey(), UUID.randomUUID().toString()));
        event.setAggregateType(notice.aggregateType());
        event.setAggregateId(notice.aggregateId());
        event.setOperation(notice.operation());
        event.setTitle(limit(notice.title()));
        event.setMessage(limitMessage(notice.message()));
        event.setSeverity(notice.severity());
        event.setTargetPath(notice.targetPath());
        event.setActorId(notice.actorId());
        event.setActorDisplayName(notice.actorDisplayName());
        event.setRecipientRoles(notice.recipientRoles().stream()
            .sorted(Comparator.comparing(Enum::name))
            .map(Enum::name)
            .collect(Collectors.joining(",")));
        event.setOccurredAt(now);
        event.setStatus(FinanceNotificationStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        financeNotificationOutboxRepository.save(event);
    }

    @Override
    public void readAll(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        ZonedDateTime now = ZonedDateTime.now();

        getRepository().findAllUnread(userId).forEach(notice -> {
            NoticesDTO noticeDTO = getMapper().toDto(notice);
            noticeDTO.setReadDate(now);
            this.update(noticeDTO.getId(), noticeDTO, abstractAuthenticationToken);
        });
    }

    @Override
    public long countUnread(AbstractAuthenticationToken abstractAuthenticationToken) {
        String userId = SecurityUtils.getUserIdFromAuthentication(abstractAuthenticationToken);
        return getRepository().countUnread(userId);
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
        getRepository().findAllByUserId(userId)
            .forEach(notices -> super.delete(notices.getId(), abstractAuthenticationToken));
    }

    private void addNoticesByRoles(String name, String message, List<RoleEnum> roles, AbstractAuthenticationToken abstractAuthenticationToken) {
        UsersCriteria criteria = new UsersCriteria();
        RoleFilter roleFilter = new RoleFilter();
        roleFilter.setIn(roles);
        criteria.setRoles(roleFilter);
        addNotices(name, message, criteria, abstractAuthenticationToken);
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_NOTICE_TEXT) return value;
        return value.substring(0, MAX_NOTICE_TEXT - 1) + "…";
    }

    private static String limitMessage(String value) {
        if (value == null || value.length() <= MAX_NOTICE_TEXT) return value;
        return value.substring(0, MAX_NOTICE_TEXT - 2) + "….";
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
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
