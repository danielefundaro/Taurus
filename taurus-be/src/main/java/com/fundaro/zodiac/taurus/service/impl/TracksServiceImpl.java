package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Tracks}.
 */
@Service
@Transactional
public class TracksServiceImpl extends CommonServiceImpl<Tracks, TracksDTO, TracksCriteria, TracksMapper, TracksRepository> implements TracksService {

    public TracksServiceImpl(TracksRepository tracksRepository, TracksMapper tracksMapper) {
        super(tracksRepository, tracksMapper, TracksService.class, Tracks.class.getSimpleName());
    }
}
