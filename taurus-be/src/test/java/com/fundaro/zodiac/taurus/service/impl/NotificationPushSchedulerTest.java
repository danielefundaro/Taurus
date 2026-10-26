package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.NotificationPushDeliveryService;
import com.fundaro.zodiac.taurus.service.PushReminderDeliveryService;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationPushSchedulerTest {

    // Collaborators are registered as plain singletons so that Spring does not autowire the mocks' inherited setters.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withInitializer(context -> {
            var beanFactory = context.getBeanFactory();
            beanFactory.registerSingleton("tenantSchemaRegistry", mock(TenantSchemaRegistry.class));
            beanFactory.registerSingleton("tenantTransactionExecutor", mock(TenantTransactionExecutor.class));
            beanFactory.registerSingleton("notificationPushDeliveryService", mock(NotificationPushDeliveryService.class));
            beanFactory.registerSingleton("pushReminderDeliveryService", mock(PushReminderDeliveryService.class));
            beanFactory.registerSingleton("notificationPreferenceMetrics", mock(NotificationPreferenceMetrics.class));
        })
        .withUserConfiguration(NotificationPushScheduler.class);

    @Test
    void schedulerIsEnabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(NotificationPushScheduler.class));
    }

    @Test
    void schedulerCanBeDisabled() {
        contextRunner
            .withPropertyValues("application.notification-push-delivery.scheduler-enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(NotificationPushScheduler.class));
    }
}
