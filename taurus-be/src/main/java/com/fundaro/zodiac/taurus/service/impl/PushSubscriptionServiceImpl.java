package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.domain.criteria.PushSubscriptionCriteria;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.PushSubscriptionService;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import com.fundaro.zodiac.taurus.service.mapper.PushSubscriptionMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class PushSubscriptionServiceImpl
    extends CommonServiceImpl<PushSubscription, PushSubscriptionDTO, PushSubscriptionCriteria, PushSubscriptionMapper, PushSubscriptionRepository>
    implements PushSubscriptionService {

    private final EntityManager entityManager;

    public PushSubscriptionServiceImpl(
        PushSubscriptionRepository repository,
        PushSubscriptionMapper mapper,
        EntityManager entityManager
    ) {
        super(repository, mapper, PushSubscriptionService.class, PushSubscription.class.getSimpleName());
        this.entityManager = entityManager;
    }

    @Override
    public PushSubscriptionDTO subscribe(PushSubscriptionDTO dto, AbstractAuthenticationToken token) {
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        lockSubscription(userId, dto.getEndpoint());

        PushSubscription existing = getRepository().findByUserIdAndEndpoint(userId, dto.getEndpoint()).orElse(null);
        if (existing != null) {
            existing.setP256dh(dto.getP256dh());
            existing.setAuth(dto.getAuth());
            existing.setDeleted(false);
            return getMapper().toDto(saveEntity(existing, token));
        }

        dto.setId(null);
        return save(dto, token);
    }

    @Override
    public void unsubscribe(String endpoint, AbstractAuthenticationToken token) {
        String userId = SecurityUtils.getUserIdFromAuthentication(token);
        getRepository().deleteByEndpointAndUserId(endpoint, userId);
    }

    private void lockSubscription(String userId, String endpoint) {
        entityManager
            .createNativeQuery(
                "select 1 from pg_advisory_xact_lock(hashtext(current_schema() || ':' || :userId), hashtext(:endpoint))"
            )
            .setParameter("userId", userId)
            .setParameter("endpoint", endpoint)
            .getSingleResult();
    }
}
