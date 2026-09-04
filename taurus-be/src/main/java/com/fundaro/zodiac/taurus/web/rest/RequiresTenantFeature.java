package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresTenantFeature {
    TenantFeature value();
}
