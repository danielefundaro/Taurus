package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;

/**
 * Service Interface for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
public interface NoticesService extends CommonService<Notices, NoticesDTO, NoticesCriteria> {
}
