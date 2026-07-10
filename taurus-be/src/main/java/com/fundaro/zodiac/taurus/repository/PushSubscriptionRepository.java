package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.domain.criteria.PushSubscriptionCriteria;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends CommonRepository<PushSubscription, PushSubscriptionCriteria> {

    List<PushSubscription> findByUserIdAndTenantCodeAndDeleted(String userId, String tenantCode, Boolean deleted);

    List<PushSubscription> findByUserIdInAndTenantCodeAndDeleted(List<String> userIds, String tenantCode, Boolean deleted);

    Optional<PushSubscription> findByUserIdAndEndpointAndTenantCode(String userId, String endpoint, String tenantCode);

    void deleteByEndpointAndUserIdAndTenantCode(String endpoint, String userId, String tenantCode);
}
