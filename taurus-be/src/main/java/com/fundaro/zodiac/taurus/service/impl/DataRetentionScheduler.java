package com.fundaro.zodiac.taurus.service.impl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.retention", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionScheduler {

    private final DataErasureService dataErasureService;

    public DataRetentionScheduler(DataErasureService dataErasureService) {
        this.dataErasureService = dataErasureService;
    }

    @Scheduled(cron = "${application.retention.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredData() {
        dataErasureService.purgeExpiredData();
    }
}
