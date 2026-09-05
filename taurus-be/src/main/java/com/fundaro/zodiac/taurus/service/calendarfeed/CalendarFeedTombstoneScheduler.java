package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.CalendarEventFeedTombstoneRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CalendarFeedTombstoneScheduler {
    private final TenantSchemaRegistry schemas; private final TenantTransactionExecutor transactions; private final CalendarEventFeedTombstoneRepository repository;
    public CalendarFeedTombstoneScheduler(TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, CalendarEventFeedTombstoneRepository repository) { this.schemas = schemas; this.transactions = transactions; this.repository = repository; }
    @Scheduled(cron = "${application.calendar-feed.cleanup-cron:0 15 3 * * *}")
    public void cleanup() { for (String tenant : schemas.findActiveTenantCodes()) transactions.execute(tenant, () -> repository.deleteByExpiresAtBefore(Instant.now())); }
}
