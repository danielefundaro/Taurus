package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring Data R2DBC repository for the Notices entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NoticesRepository extends CommonRepository<Notices, NoticesCriteria>, NoticesRepositoryInternal {
    @Override
    Flux<Notices> findAllUnread(String userId, String tenantCode);

    @Override
    Mono<Long> countUnread(String userId, String tenantCode);
}

interface NoticesRepositoryInternal extends CommonRepositoryInternal<Notices, NoticesCriteria> {
    Flux<Notices> findAllUnread(String userId, String tenantCode);

    Mono<Long> countUnread(String userId, String tenantCode);
}
