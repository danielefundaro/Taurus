package com.fundaro.zodiac.taurus.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le soglie della dashboard sono verificate all'avvio: un valore fuori dominio, o un avviso inventario più lungo
 * dell'orizzonte in cui le scadenze vengono cercate, impedisce all'applicazione di partire.
 */
class DashboardPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void theDefaultThresholdsAreValid() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            ApplicationProperties.DashboardProperties dashboard = context.getBean(ApplicationProperties.class).getDashboard();
            assertThat(dashboard.getInventoryWarningDays()).isEqualTo(7);
            assertThat(dashboard.getInventoryExpirationLookAheadDays()).isEqualTo(30);
        });
    }

    @Test
    void aWarningEqualToTheHorizonIsAccepted() {
        contextRunner
            .withPropertyValues("application.dashboard.inventory-warning-days=30", "application.dashboard.inventory-expiration-look-ahead-days=30")
            .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void aWarningBeyondTheInventoryHorizonStopsTheStartup() {
        contextRunner
            .withPropertyValues("application.dashboard.inventory-warning-days=31", "application.dashboard.inventory-expiration-look-ahead-days=30")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("inventory-warning-days must not exceed inventory-expiration-look-ahead-days");
            });
    }

    @Test
    void negativeOrTooLongWindowsStopTheStartup() {
        contextRunner.withPropertyValues("application.dashboard.calendar-look-ahead-days=-1").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("application.dashboard.calendar-look-ahead-days=367").run(context -> assertThat(context).hasFailed());
        contextRunner.withPropertyValues("application.dashboard.finance-unreconciled-warning-days=367").run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {}
}
