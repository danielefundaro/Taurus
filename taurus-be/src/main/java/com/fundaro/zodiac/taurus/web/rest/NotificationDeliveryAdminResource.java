package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.service.NotificationDeliveryAdminService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryAdminDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryCloseRequest;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryRequest;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryResult;
import jakarta.validation.Valid;
import java.time.ZonedDateTime;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notification-delivery")
public class NotificationDeliveryAdminResource {

    private final NotificationDeliveryAdminService service;

    public NotificationDeliveryAdminResource(NotificationDeliveryAdminService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotificationDeliveryAdminDTO> find(
        @RequestParam(defaultValue = "FAILED") NotificationStatus status,
        @RequestParam(required = false) NotificationDeliveryOrigin origin,
        @RequestParam(required = false) NotificationSource source,
        @RequestParam(required = false) String operation,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "occurredAt,asc") String sort
    ) {
        return service.find(status, origin, source, operation, from, to, page, size, sort);
    }

    @PostMapping("/{origin}/{id}/retry")
    public NotificationDeliveryAdminDTO retry(
        @PathVariable NotificationDeliveryOrigin origin,
        @PathVariable long id,
        AbstractAuthenticationToken authentication
    ) {
        return service.retry(origin, id, authentication);
    }

    @PostMapping("/{origin}/{id}/close")
    public NotificationDeliveryAdminDTO close(
        @PathVariable NotificationDeliveryOrigin origin,
        @PathVariable long id,
        @Valid @RequestBody NotificationDeliveryCloseRequest request,
        AbstractAuthenticationToken authentication
    ) {
        return service.close(origin, id, request.reason(), authentication);
    }

    @PostMapping("/retry")
    public NotificationDeliveryRetryResult retry(
        @Valid @RequestBody NotificationDeliveryRetryRequest request,
        AbstractAuthenticationToken authentication
    ) {
        return service.retry(request.refs(), authentication);
    }
}
