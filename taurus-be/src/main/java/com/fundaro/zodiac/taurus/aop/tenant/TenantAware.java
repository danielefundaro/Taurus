package com.fundaro.zodiac.taurus.aop.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as tenant-aware.
 * <p>
 * When applied, {@link TenantIndexAspect} will:
 * <ol>
 *   <li>Locate the first {@link org.springframework.security.authentication.AbstractAuthenticationToken}
 *       argument in the method signature.</li>
 *   <li>Extract the tenant ID via {@link com.fundaro.zodiac.taurus.security.SecurityUtils}.</li>
 *       for the duration of the call.</li>
 * </ol>
 * <p>
 * Usage example:
 * <pre>
 *   {@literal @}TenantAware
 *   public Mono{@literal <}D{@literal >} save(D dto, AbstractAuthenticationToken token) { ... }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAware {
}
