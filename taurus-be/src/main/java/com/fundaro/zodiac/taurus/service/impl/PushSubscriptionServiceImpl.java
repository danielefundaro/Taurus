package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.domain.criteria.PushSubscriptionCriteria;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.PushSubscriptionService;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import com.fundaro.zodiac.taurus.service.mapper.PushSubscriptionMapper;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PushSubscriptionServiceImpl
    extends CommonServiceImpl<PushSubscription, PushSubscriptionDTO, PushSubscriptionCriteria, PushSubscriptionMapper, PushSubscriptionRepository>
    implements PushSubscriptionService {

    public PushSubscriptionServiceImpl(PushSubscriptionRepository repository, PushSubscriptionMapper mapper) {
        super(repository, mapper, PushSubscriptionService.class, PushSubscription.class.getSimpleName());
    }

    @Override
    public PushSubscriptionDTO subscribe(PushSubscriptionDTO dto, AbstractAuthenticationToken token) {
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        // Delete any existing subscription for the same user+endpoint (upsert)
        getRepository().findByUserIdAndEndpoint(userId, dto.getEndpoint())
            .ifPresent(existing -> getRepository().deleteById(existing.getId()));
        dto.setId(null);
        return save(dto, token);
    }

    @Override
    public void unsubscribe(String endpoint, AbstractAuthenticationToken token) {
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        getRepository().deleteByEndpointAndUserId(endpoint, userId);
    }
}
