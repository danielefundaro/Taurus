package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationSeverity;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
    void addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticesSuperAdminsForTenant(String tenantCode, String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticesAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticesExcludeRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeWholeTenant(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeOnlyRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeToUser(String userId, String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeToUser(String userId, String name, String message, String actor);

    void addFinanceNoticeToUser(
        String userId,
        String eventKey,
        String name,
        String message,
        String severity,
        String targetPath,
        String actor
    );

    void enqueueFinanceNotice(FinanceNoticeCommand notice);

    void readAll(AbstractAuthenticationToken abstractAuthenticationToken);

    long countUnread(AbstractAuthenticationToken abstractAuthenticationToken);

    NoticesDTO read(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteAll(AbstractAuthenticationToken abstractAuthenticationToken);

    record FinanceNoticeCommand(
        String eventKey,
        String aggregateType,
        Long aggregateId,
        String operation,
        String title,
        String message,
        FinanceNotificationSeverity severity,
        String targetPath,
        String actorId,
        String actorDisplayName,
        Set<RoleEnum> recipientRoles
    ) {}
}
