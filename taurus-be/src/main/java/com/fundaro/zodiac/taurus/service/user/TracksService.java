package com.fundaro.zodiac.taurus.service.user;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;

public interface TracksService extends CommonOpenSearchService<Tracks, TracksDTO, TracksCriteria> {
}
