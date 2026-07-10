package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.PushSubscriptionService;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push-subscriptions")
public class PushSubscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(PushSubscriptionResource.class);

    private final PushSubscriptionService pushSubscriptionService;

    public PushSubscriptionResource(PushSubscriptionService pushSubscriptionService) {
        this.pushSubscriptionService = pushSubscriptionService;
    }

    @PostMapping
    public ResponseEntity<PushSubscriptionDTO> subscribe(@RequestBody PushSubscriptionDTO dto, AbstractAuthenticationToken token) {
        log.debug("REST request to subscribe to push notifications");
        return ResponseEntity.ok(pushSubscriptionService.subscribe(dto, token));
    }

    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint, AbstractAuthenticationToken token) {
        log.debug("REST request to unsubscribe from push notifications, endpoint={}", endpoint);
        pushSubscriptionService.unsubscribe(endpoint, token);
        return ResponseEntity.noContent().build();
    }
}
