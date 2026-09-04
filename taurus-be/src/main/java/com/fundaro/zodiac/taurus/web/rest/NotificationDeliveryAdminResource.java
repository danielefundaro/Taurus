package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.service.NotificationDeliveryAdminService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryAdminDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryRequest;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRetryResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "occurredAt,asc") String sort
    ) {
        return service.find(status, page, size, sort);
    }

    @PostMapping("/{id}/retry")
    public NotificationDeliveryAdminDTO retry(
        @PathVariable long id,
        AbstractAuthenticationToken authentication
    ) {
        return service.retry(id, authentication);
    }

    @PostMapping("/retry")
    public NotificationDeliveryRetryResult retry(
        @Valid @RequestBody NotificationDeliveryRetryRequest request,
        AbstractAuthenticationToken authentication
    ) {
        return service.retry(request.ids(), authentication);
    }
}
