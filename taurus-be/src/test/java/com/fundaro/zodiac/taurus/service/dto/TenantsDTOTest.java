package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class TenantsDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptAnUppercaseTwoLetterCountryCode() {
        TenantsDTO tenant = tenant();
        tenant.setCountry("IT");

        assertThat(validator.validate(tenant)).isEmpty();
    }

    @Test
    void shouldAcceptAnUnsetCountryCode() {
        assertThat(validator.validate(tenant())).isEmpty();
    }

    @Test
    void shouldRejectAFullCountryName() {
        TenantsDTO tenant = tenant();
        tenant.setCountry("Italia");

        assertThat(validator.validate(tenant))
            .singleElement()
            .satisfies(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("country");
                assertThat(violation.getMessage()).isEqualTo("must be a two-letter uppercase country code");
            });
    }

    @Test
    void shouldAcceptAnHttpLogoUrl() {
        TenantsDTO tenant = tenant();
        tenant.setLogoUrl("https://example.com/images/logo.png");

        assertThat(validator.validate(tenant)).isEmpty();
    }

    @Test
    void shouldAcceptAnUnsetLogoUrl() {
        assertThat(validator.validate(tenant())).isEmpty();
    }

    @Test
    void shouldRejectGenericTextAsLogoUrl() {
        TenantsDTO tenant = tenant();
        tenant.setLogoUrl("testo generico");

        assertThat(validator.validate(tenant))
            .singleElement()
            .satisfies(violation -> {
                assertThat(violation.getPropertyPath().toString()).isEqualTo("logoUrl");
                assertThat(violation.getMessage()).isEqualTo("must be a valid HTTP or HTTPS URL");
            });
    }

    @Test
    void shouldRejectNonHttpLogoUrl() {
        TenantsDTO tenant = tenant();
        tenant.setLogoUrl("ftp://example.com/logo.png");

        assertThat(validator.validate(tenant))
            .singleElement()
            .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("logoUrl"));
    }

    private TenantsDTO tenant() {
        TenantsDTO tenant = new TenantsDTO();
        tenant.setName("Tenant test");
        return tenant;
    }
}
