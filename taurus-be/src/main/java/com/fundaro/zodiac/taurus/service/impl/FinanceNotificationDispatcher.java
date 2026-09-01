package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationOutbox;
import com.fundaro.zodiac.taurus.domain.finance.FinanceNotificationStatus;
import com.fundaro.zodiac.taurus.repository.finance.FinanceNotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FinanceNotificationDispatcher {

    private static final int BATCH_SIZE = 100;
    private final FinanceNotificationOutboxRepository repository;
    private final FinanceNotificationRecipientService recipientService;
    private final NoticesService noticesService;

    public FinanceNotificationDispatcher(
        FinanceNotificationOutboxRepository repository,
        FinanceNotificationRecipientService recipientService,
        NoticesService noticesService
    ) {
        this.repository = repository;
        this.recipientService = recipientService;
        this.noticesService = noticesService;
    }

    public List<Long> findReadyIds() {
        return repository.findReadyIds(FinanceNotificationStatus.PENDING, ZonedDateTime.now(), PageRequest.of(0, BATCH_SIZE));
    }

    public void dispatch(long id) {
        FinanceNotificationOutbox event = repository.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getStatus() != FinanceNotificationStatus.PENDING || event.getNextAttemptAt().isAfter(ZonedDateTime.now())) return;
        Set<RoleEnum> roles = Arrays.stream(event.getRecipientRoles().split(","))
            .map(RoleEnum::valueOf)
            .collect(Collectors.toSet());
        Set<String> recipients = recipientService.findRecipientIds(roles);
        if (recipients.isEmpty()) throw new IllegalStateException("No active finance notification recipients are available");
        recipients.forEach(userId -> noticesService.addFinanceNoticeToUser(
            userId,
            event.getEventKey(),
            event.getTitle(),
            event.getMessage(),
            event.getSeverity().name(),
            event.getTargetPath(),
            event.getActorId()
        ));
        event.setStatus(FinanceNotificationStatus.DELIVERED);
        event.setDeliveredAt(ZonedDateTime.now());
        event.setLastError(null);
        event.touchAudit("finance-notification-dispatcher");
        repository.save(event);
    }

    public void markFailure(long id, RuntimeException exception) {
        FinanceNotificationOutbox event = repository.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getStatus() != FinanceNotificationStatus.PENDING) return;
        int attempts = event.getAttempts() + 1;
        long delayMinutes = Math.min(60, 1L << Math.min(attempts - 1, 6));
        event.setAttempts(attempts);
        event.setNextAttemptAt(ZonedDateTime.now().plus(Duration.ofMinutes(delayMinutes)));
        event.setLastError(sanitizedError(exception));
        event.touchAudit("finance-notification-dispatcher");
        repository.save(event);
    }

    public long deleteDeliveredBefore(ZonedDateTime cutoff) {
        return repository.deleteAllByStatusAndDeliveredAtBefore(FinanceNotificationStatus.DELIVERED, cutoff);
    }

    private static String sanitizedError(RuntimeException exception) {
        String message = exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage());
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
