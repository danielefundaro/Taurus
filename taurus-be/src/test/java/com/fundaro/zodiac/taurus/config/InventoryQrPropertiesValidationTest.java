package com.fundaro.zodiac.taurus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con i QR abilitati l'URL pubblico entra in ogni etichetta stampata: un valore non attendibile ferma l'avvio invece
 * di produrre etichette che porterebbero altrove. HTTP resta ammesso soltanto per gli host locali di sviluppo.
 */
class InventoryQrPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void aDisabledFeatureNeedsNoUrlAndKeepsTheDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ApplicationProperties.QrProperties qr = context.getBean(ApplicationProperties.class).getInventory().getQr();
            assertThat(qr.isEnabled()).isFalse();
            assertThat(qr.getResolutionLimitPerMinute()).isEqualTo(60);
            assertThat(qr.getLabelTimeoutSeconds()).isEqualTo(30);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example.org", "https://app.example.org/taurus", "http://localhost:4200", "http://127.0.0.1:4200", "http://[::1]:4200"})
    void acceptsHttpsOrALocalDevelopmentHost(String url) {
        contextRunner
            .withPropertyValues("application.inventory.qr.enabled=true", "application.inventory.qr.public-base-url=" + url)
            .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "app.example.org",
        "http://app.example.org",
        "https://user:secret@app.example.org",
        "https://app.example.org?tenant=demo",
        "https://app.example.org#scan",
        "ftp://app.example.org",
        "https://app example.org"
    })
    void anUntrustedUrlStopsTheStartup(String url) {
        contextRunner
            .withPropertyValues("application.inventory.qr.enabled=true", "application.inventory.qr.public-base-url=" + url)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("inventory.qr.public-base-url must be an HTTPS URL");
            });
    }

    @Test
    void theLabelTimeoutStaysWithinItsRange() {
        contextRunner.withPropertyValues("application.inventory.qr.label-timeout-seconds=0").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("application.inventory.qr.label-timeout-seconds=301").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("application.inventory.qr.label-timeout-seconds=300").run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {}
}
