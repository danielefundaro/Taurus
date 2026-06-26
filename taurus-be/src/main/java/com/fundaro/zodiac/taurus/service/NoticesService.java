package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
    void addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticesAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticesExcludeRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeWholeTenant(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void addNoticeOnlyRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    void readAll(AbstractAuthenticationToken abstractAuthenticationToken);

    long countUnread(AbstractAuthenticationToken abstractAuthenticationToken);

    NoticesDTO read(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteAll(AbstractAuthenticationToken abstractAuthenticationToken);
}
