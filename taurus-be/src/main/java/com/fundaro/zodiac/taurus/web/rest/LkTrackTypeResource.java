package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.criteria.LkTrackTypeCriteria;
import com.fundaro.zodiac.taurus.service.LkTrackTypeService;
import com.fundaro.zodiac.taurus.service.dto.LkTrackTypeDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.LkTrackType}.
 */
@RestController
@RequestMapping("/api/lk-track-types")
public class LkTrackTypeResource extends CommonResource<LkTrackType, LkTrackTypeDTO, LkTrackTypeCriteria, LkTrackTypeService> {

    public LkTrackTypeResource(LkTrackTypeService lkTrackTypeService) {
        super(lkTrackTypeService, LkTrackTypeResource.class, LkTrackType.class.getSimpleName());
    }
}
