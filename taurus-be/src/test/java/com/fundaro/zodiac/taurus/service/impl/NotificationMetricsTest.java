package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.service.notification.NotificationPendingSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationMetricsTest {

    @Test
    void recordsBoundedTenantAndSourceMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationMetrics metrics = new NotificationMetrics(registry);
        ZonedDateTime now = ZonedDateTime.now();
        NotificationPendingSummary pending = new NotificationPendingSummary() {
            @Override
            public NotificationSource getSource() { return NotificationSource.INVENTORY; }

            @Override
            public long getPendingCount() { return 3; }

            @Override
            public ZonedDateTime getOldestOccurredAt() { return now.minusMinutes(20); }
        };
        NotificationOutbox event = new NotificationOutbox();
        event.setSource(NotificationSource.INVENTORY);
        event.setOperation("ITEM_CREATED");
        event.setOccurredAt(now.minusSeconds(5));
        event.setStatus(NotificationStatus.DELIVERED);

        TenantContext.run("tenant-a", () -> {
            metrics.recordPending(List.of(pending), now);
            metrics.recordAttempt(event);
            metrics.recordDelivered(event, 2, now);
        });

        assertThat(registry.get("taurus.notifications.pending").tags("tenant", "tenant-a", "source", "INVENTORY").gauge().value())
            .isEqualTo(3);
        assertThat(registry.get("taurus.notifications.oldest.pending.seconds").tags("tenant", "tenant-a", "source", "INVENTORY").gauge().value())
            .isEqualTo(1200);
        assertThat(registry.get("taurus.notifications.attempts").tags("tenant", "tenant-a", "source", "INVENTORY", "operation", "ITEM_CREATED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("taurus.notifications.delivered").tags("tenant", "tenant-a", "source", "INVENTORY", "operation", "ITEM_CREATED").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("taurus.notifications.recipients").tags("tenant", "tenant-a", "source", "INVENTORY", "operation", "ITEM_CREATED").summary().totalAmount())
            .isEqualTo(2);
    }
}
