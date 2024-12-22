package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import com.fundaro.zodiac.taurus.service.mapper.NoticesMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@Service
@Transactional
public class NoticesServiceImpl extends CommonServiceImpl<Notices, NoticesDTO, NoticesCriteria, NoticesMapper, NoticesRepository> implements NoticesService {

    public NoticesServiceImpl(NoticesRepository noticesRepository, NoticesMapper noticesMapper) {
        super(noticesRepository, noticesMapper, NoticesService.class, Notices.class.getSimpleName());
    }
}
