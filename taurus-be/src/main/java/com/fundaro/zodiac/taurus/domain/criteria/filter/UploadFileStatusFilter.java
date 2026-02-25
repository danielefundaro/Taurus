package com.fundaro.zodiac.taurus.domain.criteria.filter;

import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import tech.jhipster.service.filter.Filter;

public class UploadFileStatusFilter extends Filter<UploadFileStatusEnum> {

    public UploadFileStatusFilter() {
    }

    public UploadFileStatusFilter(UploadFileStatusFilter filter) {
        super(filter);
    }

    @Override
    public UploadFileStatusFilter copy() {
        return new UploadFileStatusFilter(this);
    }
}
