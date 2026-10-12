package com.fundaro.zodiac.taurus.web.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks endpoints that only use the {@code public} schema or Keycloak, so they stay available when the tenant of
 * the session has no PostgreSQL schema yet (first access with the bootstrap tenant).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantSchemaNotRequired {
}
