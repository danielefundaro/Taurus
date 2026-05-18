package com.fundaro.zodiac.taurus.domain.criteria.filter;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import tech.jhipster.service.filter.Filter;

public class RoleFilter extends Filter<RoleEnum> {

    public RoleFilter() {
    }

    public RoleFilter(RoleFilter filter) {
        super(filter);
    }

    @Override
    public RoleFilter copy() {
        return new RoleFilter(this);
    }
}
