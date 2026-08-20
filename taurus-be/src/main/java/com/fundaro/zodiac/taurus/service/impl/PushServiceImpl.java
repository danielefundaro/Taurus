package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.service.PushService;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.security.Security;
import java.util.List;
import java.util.Map;

@Service
public class PushServiceImpl implements PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final ApplicationProperties.VapidProperties vapid;

    private nl.martijndwars.webpush.PushService pushService;

    private final PushSubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PushServiceImpl(PushSubscriptionRepository subscriptionRepository, ApplicationProperties applicationProperties) {
        this.subscriptionRepository = subscriptionRepository;
        vapid = applicationProperties.getVapid();
    }

    @PostConstruct
    void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        if (!"CHANGE_ME".equals(vapid.getPublicKey())) {
            this.pushService = new nl.martijndwars.webpush.PushService(vapid.getPublicKey(), vapid.getPrivateKey(), vapid.getSubject());
        } else {
            log.warn("VAPID keys not configured — push notifications are disabled. Set application.vapid.public-key and application.vapid.private-key in application.yml");
        }
    }

    @Override
    @Async
    public void sendToUser(String userId, String tenantCode, String title, String body) {
        if (pushService == null) return;
        TenantContext.run(tenantCode, () -> {
            List<PushSubscription> subs = subscriptionRepository.findByUserIdAndDeleted(userId, false);
            subs.forEach(sub -> doSend(sub, title, body));
        });
    }

    @Override
    @Async
    public void sendToUsers(List<String> userIds, String tenantCode, String title, String body) {
        if (pushService == null || userIds == null || userIds.isEmpty()) return;
        TenantContext.run(tenantCode, () -> {
            List<PushSubscription> subs = subscriptionRepository.findByUserIdInAndDeleted(userIds, false);
            subs.forEach(sub -> doSend(sub, title, body));
        });
    }

    private void doSend(PushSubscription sub, String title, String body) {
        try {
            Map<String, Object> payload = Map.of(
                "notification", Map.of(
                    "title", title,
                    "body", body,
                    "icon", "/icons/icon-192x192.png",
                    "data", Map.of("onActionClick", Map.of(
                        "default", Map.of("operation", "navigateLastFocusedOrOpen", "url", "/calendar")
                    ))
                )
            );
            String payloadJson = objectMapper.writeValueAsString(payload);

            Subscription subscription = new Subscription(
                sub.getEndpoint(),
                new Subscription.Keys(sub.getP256dh(), sub.getAuth())
            );
            Notification notification = new Notification(subscription, payloadJson);
            HttpResponse response = pushService.send(notification);

            int status = response.getStatusLine().getStatusCode();
            if (status == 410 || status == 404) {
                log.info("Push subscription expired for userId={}, removing", sub.getUserId());
                subscriptionRepository.deleteById(sub.getId());
            } else if (status >= 400) {
                log.warn("Push notification failed with status {} for userId={}", status, sub.getUserId());
            }
        } catch (Exception e) {
            log.error("Failed to send push notification to userId={}: {}", sub.getUserId(), e.getMessage());
        }
    }
}
