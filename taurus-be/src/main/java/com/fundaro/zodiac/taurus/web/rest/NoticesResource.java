package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.criteria.NoticesCriteria;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Notices}.
 */
@RestController
@RequestMapping("/api/notices")
public class NoticesResource extends CommonResource<Notices, NoticesDTO, NoticesCriteria, NoticesService> {

    public NoticesResource(NoticesService noticesService) {
        super(noticesService, NoticesResource.class, Notices.class.getSimpleName());
    }
}
