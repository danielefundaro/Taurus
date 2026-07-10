package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.domain.criteria.PushSubscriptionCriteria;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public interface PushSubscriptionService extends CommonService<PushSubscription, PushSubscriptionDTO, PushSubscriptionCriteria> {

    PushSubscriptionDTO subscribe(PushSubscriptionDTO dto, AbstractAuthenticationToken token);

    void unsubscribe(String endpoint, AbstractAuthenticationToken token);
}
