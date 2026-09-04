package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryAdminDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryResult;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryAdminService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("occurredAt", "editDate", "attempts", "status", "id");
    private final NotificationOutboxRepository repository;

    public NotificationDeliveryAdminService(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationDeliveryAdminDTO> find(
        NotificationStatus status,
        int page,
        int size,
        String sort
    ) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("Invalid page or size", "notification.delivery.page.invalid");
        Sort.Order order = parseSort(sort);
        return repository.findAllByDeletedFalseAndStatus(
            status,
            PageRequest.of(page, size, Sort.by(order))
        ).map(NotificationDeliveryAdminService::toDto);
    }

    @Transactional
    public NotificationDeliveryAdminDTO retry(long id, AbstractAuthenticationToken authentication) {
        NotificationOutbox event = repository.findByIdForUpdate(id).orElseThrow(() ->
            new RequestAlertException(HttpStatus.NOT_FOUND, "Notification delivery not found", "NotificationDelivery", "notification.delivery.notFound")
        );
        retryEvent(event, actor(authentication));
        return toDto(event);
    }

    @Transactional
    public NotificationDeliveryRetryResult retry(List<Long> rawIds, AbstractAuthenticationToken authentication) {
        if (rawIds == null || rawIds.isEmpty() || rawIds.size() > 100) {
            throw badRequest("Between 1 and 100 ids are required", "notification.delivery.ids.invalid");
        }
        Set<Long> unique = new HashSet<>(rawIds);
        if (unique.size() != rawIds.size()) throw badRequest("Duplicate ids are not allowed", "notification.delivery.ids.duplicate");
        List<Long> ids = unique.stream().sorted().toList();
        String actor = actor(authentication);
        long retried = 0;
        for (NotificationOutbox event : repository.findAllByIdsForUpdate(ids)) {
            if (event.getStatus() == NotificationStatus.FAILED) {
                retryEvent(event, actor);
                retried++;
            }
        }
        return new NotificationDeliveryRetryResult(retried);
    }

    private static boolean retryEvent(NotificationOutbox event, String actor) {
        if (event.getStatus() == NotificationStatus.PENDING) return false;
        if (event.getStatus() != NotificationStatus.FAILED) {
            throw new RequestAlertException(
                HttpStatus.CONFLICT,
                "Only failed deliveries can be retried",
                "NotificationDelivery",
                "notification.delivery.notFailed"
            );
        }
        event.setStatus(NotificationStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(ZonedDateTime.now());
        event.setDeliveredAt(null);
        event.setLastError(null);
        event.touchAudit(actor);
        return true;
    }

    private static NotificationDeliveryAdminDTO toDto(NotificationOutbox event) {
        return new NotificationDeliveryAdminDTO(
            event.getId(),
            event.getSource(),
            event.getOperation(),
            event.getStatus(),
            event.getOccurredAt(),
            event.getAttempts(),
            event.getEditDate(),
            event.getNextAttemptAt(),
            normalizedErrorClass(event.getLastError()),
            NotificationEventKey.hashForLog(event.getEventKey())
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
        if (!SORTABLE_FIELDS.contains(parts[0])) throw badRequest("Unsupported sort field", "notification.delivery.sort.invalid");
        Sort.Direction direction = parts.length == 2 && "desc".equalsIgnoreCase(parts[1])
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
        if (parts.length == 2 && !Set.of("asc", "desc").contains(parts[1].toLowerCase(Locale.ROOT))) {
            throw badRequest("Unsupported sort direction", "notification.delivery.sort.invalid");
        }
        return new Sort.Order(direction, parts[0]);
    }

    private static String actor(AbstractAuthenticationToken authentication) {
        String actor = SecurityUtils.getUserIdFromAuthentication(authentication);
        if (actor == null || actor.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Missing user claim", "NotificationDelivery", "notification.delivery.user.missing");
        }
        return actor;
    }

    private static RequestAlertException badRequest(String message, String key) {
        return new RequestAlertException(HttpStatus.BAD_REQUEST, message, "NotificationDelivery", key);
    }
}
