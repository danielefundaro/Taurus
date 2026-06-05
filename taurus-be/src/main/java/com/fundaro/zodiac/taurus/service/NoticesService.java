package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
    Mono<Void> addNoticesSuperAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> addNoticesAdmins(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> addNoticesExcludeRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> addNoticeWholeTenant(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> addNoticeOnlyRoleUsers(String name, String message, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> readAll(AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Long> countUnread(AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<NoticesDTO> read(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    Mono<Void> deleteAll(AbstractAuthenticationToken abstractAuthenticationToken);
}
