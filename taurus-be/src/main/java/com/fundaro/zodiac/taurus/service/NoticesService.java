package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.time.ZonedDateTime;

/** Service interface for user-visible notifications and idempotent outbox delivery. */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
    void addNoticeToUser(NotificationDelivery delivery);

    Long addNoticeToUserAndGetId(NotificationDelivery delivery);

    void readAll(AbstractAuthenticationToken abstractAuthenticationToken);

    long countUnread(AbstractAuthenticationToken abstractAuthenticationToken);

    NoticesDTO read(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteAll(AbstractAuthenticationToken abstractAuthenticationToken);

    NoticesDTO snooze(Long id, ZonedDateTime until, AbstractAuthenticationToken authentication);

    NoticesDTO unsnooze(Long id, AbstractAuthenticationToken authentication);
}
