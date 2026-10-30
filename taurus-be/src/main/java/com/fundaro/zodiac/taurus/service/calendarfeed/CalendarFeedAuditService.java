package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.CalendarFeedAuditRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the history of a feed credential and the matching operational log line.
 *
 * <p>The row keeps the actor, because an audit without a name answers nothing; the log line keeps
 * only its short hash, so a grep over the logs never rebuilds who reads which calendar. Neither
 * carries the token: the fingerprint is enough to tell two credentials of the same feed apart.
 */
@Service
@Transactional
public class CalendarFeedAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarFeedAuditService.class);

    private final CalendarFeedAuditRepository repository;
    private final CalendarFeedMetrics metrics;

    public CalendarFeedAuditService(CalendarFeedAuditRepository repository, CalendarFeedMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    public void record(CalendarFeedSubscription subscription, CalendarFeedAuditAction action,
                       CalendarFeedAuditSource source, String actor, String reason, Instant now) {
        CalendarFeedAuditEntry entry = new CalendarFeedAuditEntry();
        entry.setSubscriptionId(subscription.getId());
        entry.setFeedType(subscription.getFeedType());
        entry.setAction(action);
        entry.setSource(source);
        entry.setTokenVersion(subscription.getTokenVersion());
        entry.setTokenFingerprint(subscription.getTokenFingerprint());
        entry.setActor(actor == null ? "system" : actor);
        entry.setReason(reason);
        entry.setOccurredAt(now);
        repository.save(entry);
        metrics.operation(action.name(), "done");
        LOG.info("calendar_feed_credential action={} type={} source={} version={} fingerprint={} reason={} actorHash={}",
            action, subscription.getFeedType(), source, subscription.getTokenVersion(), subscription.getTokenFingerprint(),
            reason == null ? "-" : reason, shortHash(actor));
    }

    @Transactional(readOnly = true)
    public List<CalendarFeedAuditEntry> history(UUID subscriptionId) {
        return repository.findAllBySubscriptionIdOrderByOccurredAtDescIdDesc(subscriptionId);
    }

    /** Twelve hexadecimal characters: enough to follow one actor across a log, not to name them. */
    static String shortHash(String value) {
        if (value == null || value.isBlank()) return "-";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
