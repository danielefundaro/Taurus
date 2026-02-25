package com.fundaro.zodiac.taurus.domain.criteria.filter;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import tech.jhipster.service.filter.Filter;

public class StateFilter extends Filter<StateEnum> {

    public StateFilter() {
    }

    public StateFilter(StateFilter filter) {
        super(filter);
    }

    @Override
    public StateFilter copy() {
        return new StateFilter(this);
    }
}
