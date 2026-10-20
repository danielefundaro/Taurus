package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationSchedulerTest {

    // Collaborators are registered as plain singletons so that Spring does not autowire the mocks' inherited setters.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(context -> {
            var beanFactory = context.getBeanFactory();
            beanFactory.registerSingleton("tenantSchemaRegistry", mock(TenantSchemaRegistry.class));
            beanFactory.registerSingleton("tenantTransactionExecutor", mock(TenantTransactionExecutor.class));
            beanFactory.registerSingleton("notificationDispatcher", mock(NotificationDispatcher.class));
            beanFactory.registerSingleton("notificationMetrics", mock(NotificationMetrics.class));
            beanFactory.registerSingleton("applicationProperties", new ApplicationProperties());
        })
        .withUserConfiguration(NotificationScheduler.class);

    @Test
    void schedulerIsEnabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(NotificationScheduler.class));
    }

    @Test
    void schedulerCanBeDisabled() {
        contextRunner
            .withPropertyValues("application.notifications.scheduler-enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(NotificationScheduler.class));
    }
}
