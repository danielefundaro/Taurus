package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics.FanoutChannel;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics.FanoutResult;
import com.fundaro.zodiac.taurus.service.notification.NotificationTiming;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

@Service
public class NotificationPushDeliveryService {

    static final String ACTOR = "notification-push-dispatcher";
    private final NotificationPushDeliveryRepository repository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final EntityManager entityManager;
    private final UsersRepository usersRepository;
    private final NotificationPreferenceResolver preferenceResolver;
    private final PushService pushService;
    private final ApplicationProperties.NotificationPushDeliveryProperties properties;
    private NotificationPreferenceMetrics preferenceMetrics;

    public NotificationPushDeliveryService(
        NotificationPushDeliveryRepository repository,
        PushSubscriptionRepository subscriptionRepository,
        EntityManager entityManager,
        UsersRepository usersRepository,
        NotificationPreferenceResolver preferenceResolver,
        PushService pushService,
        ApplicationProperties applicationProperties
    ) {
        this.repository = repository;
        this.subscriptionRepository = subscriptionRepository;
        this.entityManager = entityManager;
        this.usersRepository = usersRepository;
        this.preferenceResolver = preferenceResolver;
        this.pushService = pushService;
        this.properties = applicationProperties.getNotificationPushDelivery();
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setPreferenceMetrics(NotificationPreferenceMetrics preferenceMetrics) {
        this.preferenceMetrics = preferenceMetrics;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(NotificationOutbox event, NotificationPreferenceDecision preference, Long noticeId) {
        if (preference.pushMode() == NotificationPushMode.OFF) {
            recordDecision(event.getSource(), FanoutResult.SUPPRESSED);
            return;
        }
        NotificationPushDeliveryType type = preference.pushMode() == NotificationPushMode.IMMEDIATE
            ? NotificationPushDeliveryType.IMMEDIATE
            : NotificationPushDeliveryType.DIGEST_ITEM;
        if (repository.existsBySourceEventKeyAndUserIdAndDeliveryTypeAndDeletedFalse(event.getEventKey(), preference.userId(), type)) return;

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime scheduledAt = type == NotificationPushDeliveryType.IMMEDIATE
            ? NotificationTiming.nextAllowed(preference, now)
            : NotificationTiming.nextDigest(preference, now);
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.initializeAudit(ACTOR);
        delivery.setSourceEventKey(event.getEventKey());
        delivery.setUserId(preference.userId());
        delivery.setSource(event.getSource());
        delivery.setDeliveryType(type);
        delivery.setTitle(event.getTitle());
        delivery.setMessage(event.getMessage());
        delivery.setTargetPath(event.getTargetPath());
        if (noticeId != null) delivery.setNotice(entityManager.getReference(Notices.class, noticeId));
        if (type == NotificationPushDeliveryType.DIGEST_ITEM) {
            delivery.setDigestLocalDate(scheduledAt.withZoneSameInstant(preference.timeZone()).toLocalDate());
        }
        delivery.setScheduledAt(scheduledAt);
        delivery.setExpiresAt(scheduledAt.plusHours(properties.getDefaultExpirationHours()));
        delivery.setStatus(NotificationStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(scheduledAt);
        repository.save(delivery);
        recordDecision(event.getSource(), FanoutResult.DELIVERED);
        recordJob(type.name(), NotificationStatus.PENDING);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueSnooze(Notices notice, NotificationPreferenceDecision preference) {
        if (!subscriptionRepository.existsByUserIdAndDeletedFalse(notice.getUserId())) return;
        if (repository.existsByNoticeIdAndSnoozeRevisionAndDeliveryTypeAndDeletedFalse(
            notice.getId(), notice.getSnoozeRevision(), NotificationPushDeliveryType.SNOOZE)) return;
        ZonedDateTime scheduledAt = NotificationTiming.nextAllowed(preference, notice.getSnoozedUntil());
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.initializeAudit(ACTOR);
        delivery.setSourceEventKey(notice.getSourceEventKey() == null ? "notice:" + notice.getId() : notice.getSourceEventKey());
        delivery.setUserId(notice.getUserId());
        delivery.setSource(com.fundaro.zodiac.taurus.domain.notification.NotificationSource.valueOf(notice.getSource()));
        delivery.setDeliveryType(NotificationPushDeliveryType.SNOOZE);
        delivery.setTitle(notice.getName());
        delivery.setMessage(notice.getMessage() == null ? "" : notice.getMessage());
        delivery.setTargetPath(notice.getTargetPath());
        delivery.setNotice(notice);
        delivery.setSnoozeRevision(notice.getSnoozeRevision());
        delivery.setScheduledAt(scheduledAt);
        delivery.setExpiresAt(scheduledAt.plusHours(properties.getDefaultExpirationHours()));
        delivery.setStatus(NotificationStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(scheduledAt);
        repository.save(delivery);
        recordJob(NotificationPushDeliveryType.SNOOZE.name(), NotificationStatus.PENDING);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelSnooze(Notices notice) {
        repository.findAllByNoticeIdAndDeliveryTypeAndStatusAndDeletedFalse(
            notice.getId(), NotificationPushDeliveryType.SNOOZE, NotificationStatus.PENDING
        ).forEach(delivery -> {
            delivery.setStatus(NotificationStatus.SKIPPED);
            delivery.setSkipReason("NOTICE_CHANGED");
            delivery.setNextAttemptAt(null);
            delivery.touchAudit(ACTOR);
        });
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Long> findReadyIds() {
        return repository.findReadyIds(NotificationStatus.PENDING, ZonedDateTime.now(), PageRequest.of(0, Math.max(1, properties.getBatchSize())));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void process(long id) {
        ZonedDateTime now = ZonedDateTime.now();
        NotificationPushDelivery delivery = repository.findByIdForUpdate(id).orElse(null);
        if (delivery == null || delivery.getStatus() != NotificationStatus.PENDING || delivery.getNextAttemptAt().isAfter(now)) return;
        if (preferenceMetrics != null) {
            preferenceMetrics.recordDispatchDelay(delivery.getDeliveryType().name(), delivery.getScheduledAt(), now);
        }
        if (delivery.getDeliveryType() == NotificationPushDeliveryType.DIGEST_ITEM) {
            processDigest(delivery, now);
        } else {
            processSingle(delivery, now);
        }
    }

    private void processSingle(NotificationPushDelivery delivery, ZonedDateTime now) {
        if (!eligibleUser(delivery.getUserId())) {
            skip(delivery, "USER_INACTIVE");
            return;
        }
        if (noticeNoLongerEligible(delivery)) {
            skip(delivery, "NOTICE_CHANGED");
            return;
        }
        NotificationPreferenceDecision preference = currentPreference(delivery);
        if (delivery.getDeliveryType() == NotificationPushDeliveryType.IMMEDIATE && preference.pushMode() != NotificationPushMode.IMMEDIATE) {
            skip(delivery, "PREFERENCE_CHANGED");
            return;
        }
        if (now.isAfter(delivery.getExpiresAt())) {
            skip(delivery, "EXPIRED");
            return;
        }
        ZonedDateTime allowed = NotificationTiming.nextAllowed(preference, now);
        if (allowed.isAfter(now.plusSeconds(1))) {
            if (allowed.isAfter(delivery.getExpiresAt())) skip(delivery, "EXPIRED");
            else {
                delivery.setNextAttemptAt(allowed);
                delivery.touchAudit(ACTOR);
            }
            return;
        }
        String title = preference.pushPreview() == NotificationPushPreview.PRIVATE ? "Taurus" : delivery.getTitle();
        String body = preference.pushPreview() == NotificationPushPreview.PRIVATE
            ? (delivery.getDeliveryType() == NotificationPushDeliveryType.SNOOZE ? "Hai una notifica da rivedere" : "Hai un nuovo aggiornamento")
            : delivery.getMessage();
        applyResult(delivery, pushService.sendToUserNow(delivery.getUserId(), tenant(), title, body, delivery.getTargetPath()), now);
    }

    private void processDigest(NotificationPushDelivery seed, ZonedDateTime now) {
        List<NotificationPushDelivery> bucket = repository.findDigestForUpdate(
            seed.getUserId(), seed.getDigestLocalDate(), NotificationPushDeliveryType.DIGEST_ITEM, NotificationStatus.PENDING, now
        );
        Map<com.fundaro.zodiac.taurus.domain.notification.NotificationSource, Integer> counts = new EnumMap<>(com.fundaro.zodiac.taurus.domain.notification.NotificationSource.class);
        List<NotificationPushDelivery> eligible = bucket.stream().filter(delivery -> {
            if (!eligibleUser(delivery.getUserId()) || noticeNoLongerEligible(delivery) || now.isAfter(delivery.getExpiresAt())) return false;
            NotificationPreferenceDecision preference = currentPreference(delivery);
            return preference.pushMode() == NotificationPushMode.DAILY_DIGEST;
        }).toList();
        bucket.stream().filter(delivery -> !eligible.contains(delivery)).forEach(delivery -> skip(delivery, "NOT_ELIGIBLE"));
        if (eligible.isEmpty()) return;
        if (preferenceMetrics != null) preferenceMetrics.recordDigestSize(eligible.size());
        eligible.forEach(delivery -> counts.merge(delivery.getSource(), 1, Integer::sum));
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        String summary = counts.entrySet().stream()
            .map(entry -> entry.getValue() + " " + categoryName(entry.getKey()))
            .collect(Collectors.joining(", "));
        PushDeliveryResult result = pushService.sendToUserNow(
            seed.getUserId(), tenant(), "Taurus: riepilogo giornaliero", "Hai " + total + " aggiornamenti: " + summary + ".", "/dashboard?section=notifications"
        );
        eligible.forEach(delivery -> applyResult(delivery, result, now));
    }

    private NotificationPreferenceDecision currentPreference(NotificationPushDelivery delivery) {
        return preferenceResolver.resolve(
            delivery.getSource(), NotificationPreferencePolicy.CONFIGURABLE, Set.of(delivery.getUserId())
        ).get(delivery.getUserId());
    }

    private boolean eligibleUser(String userId) {
        return usersRepository.findByKeycloakIdAndDeletedFalse(userId).map(user -> Boolean.TRUE.equals(user.getActive())).orElse(false);
    }

    private static boolean noticeNoLongerEligible(NotificationPushDelivery delivery) {
        Notices notice = delivery.getNotice();
        if (notice == null) return false;
        if (Boolean.TRUE.equals(notice.getDeleted()) || notice.getReadDate() != null) return true;
        return delivery.getDeliveryType() == NotificationPushDeliveryType.SNOOZE &&
            (delivery.getSnoozeRevision() != notice.getSnoozeRevision() || notice.getSnoozedUntil() == null);
    }

    private void applyResult(NotificationPushDelivery delivery, PushDeliveryResult result, ZonedDateTime now) {
        if (preferenceMetrics != null) preferenceMetrics.recordSubscriptionsRemoved(result.invalid());
        if (result.delivered()) {
            delivery.setStatus(NotificationStatus.DELIVERED);
            delivery.setDeliveredAt(now);
            delivery.setNextAttemptAt(null);
            delivery.setLastError(null);
            recordJob(delivery.getDeliveryType().name(), NotificationStatus.DELIVERED);
        } else if (result.devices() == 0) {
            skip(delivery, "NO_SUBSCRIPTION");
            return;
        } else if (result.retryable() && delivery.getAttempts() + 1 < properties.getMaxAttempts()) {
            int attempts = delivery.getAttempts() + 1;
            delivery.setAttempts(attempts);
            delivery.setNextAttemptAt(now.plus(retryDelay(attempts)));
            delivery.setLastError("TEMPORARY_PROVIDER_FAILURE");
            if (preferenceMetrics != null) {
                preferenceMetrics.recordRetry(delivery.getDeliveryType().name(), "TEMPORARY_PROVIDER_FAILURE");
            }
        } else {
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setStatus(NotificationStatus.FAILED);
            delivery.setNextAttemptAt(null);
            delivery.setLastError(result.retryable() ? "TEMPORARY_PROVIDER_FAILURE" : "PERMANENT_PROVIDER_FAILURE");
            recordJob(delivery.getDeliveryType().name(), NotificationStatus.FAILED);
        }
        delivery.touchAudit(ACTOR);
    }

    private Duration retryDelay(int attempts) {
        long initial = Math.max(1, properties.getRetryInitialMinutes());
        long delay = initial * (1L << Math.min(20, Math.max(0, attempts - 1)));
        return Duration.ofMinutes(Math.min(Math.max(initial, properties.getRetryMaxMinutes()), delay));
    }

    private static String categoryName(com.fundaro.zodiac.taurus.domain.notification.NotificationSource source) {
        return switch (source) {
            case CALENDAR -> "calendario";
            case INVENTORY -> "inventario";
            case FINANCE -> "economia";
            case CONTENT -> "contenuti";
            case IDENTITY -> "utenti e accessi";
            case TENANT -> "organizzazione";
            case GENERAL -> "generali";
        };
    }

    private void recordDecision(com.fundaro.zodiac.taurus.domain.notification.NotificationSource source, FanoutResult result) {
        if (preferenceMetrics != null) preferenceMetrics.recordFanoutDecision(source, FanoutChannel.PUSH, result);
    }

    private void recordJob(String deliveryType, NotificationStatus status) {
        if (preferenceMetrics != null) preferenceMetrics.recordJob(deliveryType, status);
    }

    private void skip(NotificationPushDelivery delivery, String reason) {
        recordJob(delivery.getDeliveryType().name(), NotificationStatus.SKIPPED);
        delivery.setStatus(NotificationStatus.SKIPPED);
        delivery.setSkipReason(reason);
        delivery.setNextAttemptAt(null);
        delivery.setLastError(null);
        delivery.touchAudit(ACTOR);
    }

    private static String tenant() {
        return TenantContext.getTenantCode().orElseThrow();
    }
}
