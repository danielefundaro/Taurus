package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Service interface for user-visible notifications and idempotent outbox delivery. */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
    void addNoticeToUser(NotificationDelivery delivery);

    void readAll(AbstractAuthenticationToken abstractAuthenticationToken);

    long countUnread(AbstractAuthenticationToken abstractAuthenticationToken);

    NoticesDTO read(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteAll(AbstractAuthenticationToken abstractAuthenticationToken);
}
