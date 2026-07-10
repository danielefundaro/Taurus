package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

@ParameterObject
public class PushSubscriptionCriteria extends CommonCriteria {

    private StringFilter endpoint;

    public PushSubscriptionCriteria() {
        super();
    }

    public PushSubscriptionCriteria(PushSubscriptionCriteria other) {
        super(other);
        this.endpoint = other.optionalEndpoint().map(StringFilter::copy).orElse(null);
    }

    @Override
    public PushSubscriptionCriteria copy() {
        return new PushSubscriptionCriteria(this);
    }

    public StringFilter getEndpoint() { return endpoint; }
    public Optional<StringFilter> optionalEndpoint() { return Optional.ofNullable(endpoint); }
    public StringFilter endpoint() { if (endpoint == null) { setEndpoint(new StringFilter()); } return endpoint; }
    public void setEndpoint(StringFilter endpoint) { this.endpoint = endpoint; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PushSubscriptionCriteria that = (PushSubscriptionCriteria) o;
        return Objects.equals(endpoint, that.endpoint);
    }

    @Override
    public int hashCode() { return Objects.hash(super.hashCode(), endpoint); }
}
