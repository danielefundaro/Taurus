package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.criteria.CollectionsCriteria;
import com.fundaro.zodiac.taurus.service.CollectionsService;
import com.fundaro.zodiac.taurus.service.dto.CollectionsDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Collections}.
 */
@RestController
@RequestMapping("/api/collections")
public class CollectionsResource extends CommonResource<Collections, CollectionsDTO, CollectionsCriteria, CollectionsService> {

    public CollectionsResource(CollectionsService collectionsService) {
        super(collectionsService, CollectionsResource.class, Collections.class.getSimpleName());
    }
}
