package com.fundaro.zodiac.taurus.test.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * Autentica il test con un {@code JwtAuthenticationToken}, non con la username/password di
 * {@code @WithMockUser}.
 *
 * <p>È la differenza che conta per gli integration test delle risorse: sia
 * {@link com.fundaro.zodiac.taurus.multitenancy.TenantContextInterceptor} sia
 * {@link com.fundaro.zodiac.taurus.security.SecurityUtils} leggono tenant e utente dai claim del
 * token e ignorano (o rifiutano) qualsiasi altro tipo di autenticazione.
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@WithSecurityContext(factory = WithMockTenantUserSecurityContextFactory.class)
public @interface WithMockTenantUser {
    /** Valore del claim {@code sub}: è l'identità con cui i servizi filtrano le righe possedute. */
    String userId() default "AAAAAAAAAA";

    /** Valore del claim {@code tenant}: seleziona lo schema PostgreSQL della richiesta. */
    String tenant() default "resource-it-tenant";

    String[] authorities() default { "ROLE_USER" };
}
