package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import org.springframework.stereotype.Repository;

/**
 * Spring Data R2DBC repository for the Notices entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NoticesRepository extends CommonRepository<Notices, NoticesCriteria>, NoticesRepositoryInternal {
}

interface NoticesRepositoryInternal extends CommonRepositoryInternal<Notices, NoticesCriteria> {
}
