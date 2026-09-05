package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliveryFilter;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliveryRow;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryAdminDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRef;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryResult;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Console tecnica delle consegne notifiche.
 *
 * <p>Copre le tre origini previste dalla specifica: il fan-out in-app
 * ({@code OUTBOX}, solo retry) e le due code push ({@code PUSH} e
 * {@code REMINDER}, retry oppure chiusura tecnica motivata).
 *
 * <p>Nessun metodo espone testo editoriale, destinatario, endpoint o chiavi push.
 */
@Service
public class NotificationDeliveryAdminService {

    static final String ENTITY = "NotificationDelivery";

    private final NotificationDeliveryAdminQueryRepository queryRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final NotificationPushDeliveryRepository pushDeliveryRepository;
    private final PushReminderRepository reminderRepository;
    private final ApplicationProperties.NotificationPushDeliveryProperties pushProperties;

    public NotificationDeliveryAdminService(
        NotificationDeliveryAdminQueryRepository queryRepository,
        NotificationOutboxRepository outboxRepository,
        NotificationPushDeliveryRepository pushDeliveryRepository,
        PushReminderRepository reminderRepository,
        ApplicationProperties applicationProperties
    ) {
        this.queryRepository = queryRepository;
        this.outboxRepository = outboxRepository;
        this.pushDeliveryRepository = pushDeliveryRepository;
        this.reminderRepository = reminderRepository;
        this.pushProperties = applicationProperties.getNotificationPushDelivery();
    }

    @Transactional(readOnly = true)
    public Page<NotificationDeliveryAdminDTO> find(
        NotificationStatus status,
        NotificationDeliveryOrigin origin,
        NotificationSource source,
        String operation,
        ZonedDateTime from,
        ZonedDateTime to,
        int page,
        int size,
        String sort
    ) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("Invalid page or size", "notification.delivery.page.invalid");
        if (from != null && to != null && !from.isBefore(to)) {
            throw badRequest("The range start must precede its end", "notification.delivery.range.invalid");
        }
        if (operation != null && !operation.isBlank() && !operation.matches("[A-Za-z0-9_]{1,64}")) {
            throw badRequest("Unsupported operation filter", "notification.delivery.operation.invalid");
        }
        Sort.Order order = parseSort(sort);
        NotificationDeliveryFilter filter = new NotificationDeliveryFilter(status, origin, source, operation, from, to);
        long total = queryRepository.count(filter);
        List<NotificationDeliveryAdminDTO> content = queryRepository
            .find(filter, page, size, order.getProperty(), order.isDescending())
            .stream()
            .map(NotificationDeliveryAdminService::toDto)
            .toList();
        return new PageImpl<>(content, PageRequest.of(page, size, Sort.by(order)), total);
    }

    @Transactional
    public NotificationDeliveryAdminDTO retry(NotificationDeliveryOrigin origin, long id, AbstractAuthenticationToken authentication) {
        String actor = actor(authentication);
        return switch (origin) {
            case OUTBOX -> {
                NotificationOutbox event = lockedOutbox(id);
                retryOutbox(event, actor);
                yield toDto(event);
            }
            case PUSH -> {
                NotificationPushDelivery delivery = lockedPush(id);
                retryPush(delivery, actor);
                yield toDto(delivery);
            }
            case REMINDER -> {
                PushReminder reminder = lockedReminder(id);
                retryReminder(reminder, actor);
                yield toDto(reminder);
            }
        };
    }

    @Transactional
    public NotificationDeliveryRetryResult retry(List<NotificationDeliveryRef> refs, AbstractAuthenticationToken authentication) {
        if (refs == null || refs.isEmpty() || refs.size() > 100) {
            throw badRequest("Between 1 and 100 references are required", "notification.delivery.ids.invalid");
        }
        Set<NotificationDeliveryRef> unique = new HashSet<>(refs);
        if (unique.size() != refs.size()) throw badRequest("Duplicate references are not allowed", "notification.delivery.ids.duplicate");
        String actor = actor(authentication);
        long retried = 0;
        // Ordine deterministico per origine e id: evita deadlock tra retry massivi concorrenti.
        List<NotificationDeliveryRef> ordered = refs.stream()
            .sorted(java.util.Comparator.comparing((NotificationDeliveryRef ref) -> ref.origin().name()).thenComparing(NotificationDeliveryRef::id))
            .toList();
        for (NotificationDeliveryRef ref : ordered) {
            if (retryIfFailed(ref, actor)) retried++;
        }
        return new NotificationDeliveryRetryResult(retried);
    }

    /**
     * Chiusura tecnica motivata: applicabile soltanto alle code push, perché il
     * fan-out in-app non ha uno stato {@code SKIPPED} amministrativo.
     */
    @Transactional
    public NotificationDeliveryAdminDTO close(
        NotificationDeliveryOrigin origin,
        long id,
        String reason,
        AbstractAuthenticationToken authentication
    ) {
        String actor = actor(authentication);
        if (origin == NotificationDeliveryOrigin.OUTBOX) {
            throw new RequestAlertException(
                HttpStatus.CONFLICT,
                "In-app fan-out events cannot be closed technically",
                ENTITY,
                "notification.delivery.close.unsupported"
            );
        }
        if (origin == NotificationDeliveryOrigin.PUSH) {
            NotificationPushDelivery delivery = lockedPush(id);
            requireClosable(delivery.getStatus());
            delivery.setStatus(NotificationStatus.SKIPPED);
            delivery.setSkipReason(reason);
            delivery.setNextAttemptAt(null);
            delivery.setLastError(null);
            delivery.touchAudit(actor);
            return toDto(delivery);
        }
        PushReminder reminder = lockedReminder(id);
        requireClosable(reminder.getStatus());
        reminder.setStatus(NotificationStatus.SKIPPED);
        reminder.setSkipReason(reason);
        reminder.setSent(true);
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);
        reminder.touchAudit(actor);
        return toDto(reminder);
    }

    private boolean retryIfFailed(NotificationDeliveryRef ref, String actor) {
        switch (ref.origin()) {
            case OUTBOX -> {
                NotificationOutbox event = lockedOutbox(ref.id());
                if (event.getStatus() != NotificationStatus.FAILED) return false;
                retryOutbox(event, actor);
                return true;
            }
            case PUSH -> {
                NotificationPushDelivery delivery = lockedPush(ref.id());
                if (delivery.getStatus() != NotificationStatus.FAILED) return false;
                retryPush(delivery, actor);
                return true;
            }
            default -> {
                PushReminder reminder = lockedReminder(ref.id());
                if (reminder.getStatus() != NotificationStatus.FAILED) return false;
                retryReminder(reminder, actor);
                return true;
            }
        }
    }

    private static void retryOutbox(NotificationOutbox event, String actor) {
        if (event.getStatus() == NotificationStatus.PENDING) return;
        requireFailed(event.getStatus());
        event.setStatus(NotificationStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(ZonedDateTime.now());
        event.setDeliveredAt(null);
        event.setLastError(null);
        event.touchAudit(actor);
    }

    /**
     * Il dispatcher rivalida utente, preferenze e sottoscrizioni prima di inviare:
     * qui si riapre soltanto la finestra di consegna, se nel frattempo è scaduta.
     */
    private void retryPush(NotificationPushDelivery delivery, String actor) {
        if (delivery.getStatus() == NotificationStatus.PENDING) return;
        requireFailed(delivery.getStatus());
        ZonedDateTime now = ZonedDateTime.now();
        delivery.setStatus(NotificationStatus.PENDING);
        delivery.setAttempts(0);
        delivery.setNextAttemptAt(now);
        delivery.setDeliveredAt(null);
        delivery.setSkipReason(null);
        delivery.setLastError(null);
        if (delivery.getExpiresAt() == null || !delivery.getExpiresAt().isAfter(now)) {
            delivery.setExpiresAt(now.plusHours(pushProperties.getDefaultExpirationHours()));
        }
        delivery.touchAudit(actor);
    }

    /**
     * Un promemoria riaperto resta soggetto alla regola dell'inizio evento: se
     * l'occorrenza è già iniziata lo scheduler lo chiuderà come {@code SKIPPED}.
     */
    private static void retryReminder(PushReminder reminder, String actor) {
        if (reminder.getStatus() == NotificationStatus.PENDING) return;
        requireFailed(reminder.getStatus());
        ZonedDateTime now = ZonedDateTime.now();
        reminder.setStatus(NotificationStatus.PENDING);
        reminder.setSent(false);
        reminder.setAttempts(0);
        reminder.setSendAt(now.toInstant());
        reminder.setNextAttemptAt(now);
        reminder.setDeliveredAt(null);
        reminder.setSkipReason(null);
        reminder.setLastError(null);
        reminder.touchAudit(actor);
    }

    private NotificationOutbox lockedOutbox(long id) {
        return outboxRepository.findByIdForUpdate(id).orElseThrow(NotificationDeliveryAdminService::notFound);
    }

    private NotificationPushDelivery lockedPush(long id) {
        return pushDeliveryRepository.findByIdForAdminUpdate(id).orElseThrow(NotificationDeliveryAdminService::notFound);
    }

    private PushReminder lockedReminder(long id) {
        return reminderRepository.findByIdForUpdate(id).orElseThrow(NotificationDeliveryAdminService::notFound);
    }

    private static void requireFailed(NotificationStatus status) {
        if (status != NotificationStatus.FAILED) {
            throw new RequestAlertException(
                HttpStatus.CONFLICT,
                "Only failed deliveries can be retried",
                ENTITY,
                "notification.delivery.notFailed"
            );
        }
    }

    private static void requireClosable(NotificationStatus status) {
        if (status != NotificationStatus.FAILED && status != NotificationStatus.PENDING) {
            throw new RequestAlertException(
                HttpStatus.CONFLICT,
                "Only pending or failed deliveries can be closed",
                ENTITY,
                "notification.delivery.notClosable"
            );
        }
    }

    private static RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Notification delivery not found", ENTITY, "notification.delivery.notFound");
    }

    private static NotificationDeliveryAdminDTO toDto(NotificationDeliveryRow row) {
        return new NotificationDeliveryAdminDTO(
            NotificationDeliveryAdminDTO.rowKey(row.origin(), row.id()),
            row.id(),
            row.origin(),
            row.source(),
            row.operation(),
            row.deliveryType(),
            row.status(),
            row.occurredAt(),
            row.attempts(),
            row.editDate(),
            row.nextAttemptAt(),
            normalizedErrorClass(row.lastError()),
            row.skipReason(),
            NotificationEventKey.hashForLog(row.eventKey())
        );
    }

    private static NotificationDeliveryAdminDTO toDto(NotificationOutbox event) {
        return new NotificationDeliveryAdminDTO(
            NotificationDeliveryAdminDTO.rowKey(NotificationDeliveryOrigin.OUTBOX, event.getId()),
            event.getId(),
            NotificationDeliveryOrigin.OUTBOX,
            event.getSource(),
            event.getOperation(),
            "IN_APP_FANOUT",
            event.getStatus(),
            event.getOccurredAt(),
            event.getAttempts(),
            event.getEditDate(),
            event.getNextAttemptAt(),
            normalizedErrorClass(event.getLastError()),
            null,
            NotificationEventKey.hashForLog(event.getEventKey())
        );
    }

    private static NotificationDeliveryAdminDTO toDto(NotificationPushDelivery delivery) {
        return new NotificationDeliveryAdminDTO(
            NotificationDeliveryAdminDTO.rowKey(NotificationDeliveryOrigin.PUSH, delivery.getId()),
            delivery.getId(),
            NotificationDeliveryOrigin.PUSH,
            delivery.getSource(),
            null,
            delivery.getDeliveryType().name(),
            delivery.getStatus(),
            delivery.getScheduledAt(),
            delivery.getAttempts(),
            delivery.getEditDate(),
            delivery.getNextAttemptAt(),
            normalizedErrorClass(delivery.getLastError()),
            delivery.getSkipReason(),
            NotificationEventKey.hashForLog(delivery.getSourceEventKey())
        );
    }

    private static NotificationDeliveryAdminDTO toDto(PushReminder reminder) {
        return new NotificationDeliveryAdminDTO(
            NotificationDeliveryAdminDTO.rowKey(NotificationDeliveryOrigin.REMINDER, reminder.getId()),
            reminder.getId(),
            NotificationDeliveryOrigin.REMINDER,
            NotificationSource.CALENDAR,
            null,
            "EVENT_REMINDER",
            reminder.getStatus(),
            reminder.getSendAt() == null ? null : reminder.getSendAt().atZone(java.time.ZoneId.systemDefault()),
            reminder.getAttempts(),
            reminder.getEditDate(),
            reminder.getNextAttemptAt(),
            normalizedErrorClass(reminder.getLastError()),
            reminder.getSkipReason(),
            NotificationEventKey.hashForLog("reminder:" + reminder.getEventId() + ":" + reminder.getId())
        );
    }

    private static String normalizedErrorClass(String lastError) {
        if (lastError == null || lastError.isBlank()) return null;
        String className = lastError.split(":", 2)[0].trim();
        int separator = className.lastIndexOf('.');
        String normalized = separator >= 0 ? className.substring(separator + 1) : className;
        return normalized.matches("[A-Za-z0-9_$]+") ? normalized : "DeliveryFailure";
    }

    private static Sort.Order parseSort(String value) {
        String normalized = value == null || value.isBlank() ? "occurredAt,asc" : value.trim();
        String[] parts = normalized.split(",", 2);
        if (!NotificationDeliveryAdminQueryRepository.sortableFields().contains(parts[0])) {
            throw badRequest("Unsupported sort field", "notification.delivery.sort.invalid");
        }
        if (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase(Locale.ROOT))) {
            throw badRequest("Unsupported sort direction", "notification.delivery.sort.invalid");
        }
        Sort.Direction direction = parts.length == 2 && "desc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        return new Sort.Order(direction, parts[0]);
    }

    private static String actor(AbstractAuthenticationToken authentication) {
        String actor = SecurityUtils.getUserIdFromAuthentication(authentication);
        if (actor == null || actor.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Missing user claim", ENTITY, "notification.delivery.user.missing");
        }
        return actor;
    }

    private static RequestAlertException badRequest(String message, String key) {
        return new RequestAlertException(HttpStatus.BAD_REQUEST, message, ENTITY, key);
    }
}
