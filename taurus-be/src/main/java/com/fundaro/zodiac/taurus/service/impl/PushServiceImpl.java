package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.service.PushService;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;
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
        sendToUserNow(userId, tenantCode, title, body, "/calendar");
    }

    @Override
    @Async
    public void sendToUsers(List<String> userIds, String tenantCode, String title, String body) {
        if (pushService == null || userIds == null || userIds.isEmpty()) return;
        TenantContext.run(tenantCode, () -> {
            List<PushSubscription> subs = subscriptionRepository.findByUserIdInAndDeleted(userIds, false);
            subs.forEach(sub -> doSend(sub, title, body, "/calendar"));
        });
    }

    @Override
    public PushDeliveryResult sendToUserNow(String userId, String tenantCode, String title, String body, String targetPath) {
        if (pushService == null) return new PushDeliveryResult(0, 0, 0, 1, 1);
        return TenantContext.call(tenantCode, () -> {
            List<PushSubscription> subscriptions = subscriptionRepository.findByUserIdAndDeleted(userId, false);
            if (subscriptions.isEmpty()) return PushDeliveryResult.noDevices();
            int accepted = 0;
            int invalid = 0;
            int temporary = 0;
            int permanent = 0;
            for (PushSubscription subscription : subscriptions) {
                int outcome = doSend(subscription, title, body, targetPath);
                if (outcome == 1) accepted++;
                else if (outcome == 2) invalid++;
                else if (outcome == 3) temporary++;
                else permanent++;
            }
            return new PushDeliveryResult(accepted, invalid, temporary, permanent, subscriptions.size());
        });
    }

    private int doSend(PushSubscription sub, String title, String body, String targetPath) {
        try {
            Map<String, Object> payload = Map.of(
                "notification", Map.of(
                    "title", title,
                    "body", body,
                    "icon", "/icons/icon-192x192.png",
                    "data", Map.of("onActionClick", Map.of(
                        "default", Map.of("operation", "navigateLastFocusedOrOpen", "url", targetPath == null ? "/dashboard?section=notifications" : targetPath)
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
                sub.setDeleted(true);
                subscriptionRepository.save(sub);
                return 2;
            } else if (status == 429 || status >= 500) {
                log.warn("Temporary Web Push provider failure with status {}", status);
                return 3;
            } else if (status >= 400) {
                log.warn("Permanent Web Push provider failure with status {}", status);
                return 4;
            }
            return 1;
        } catch (Exception e) {
            log.warn("Temporary Web Push provider failure: {}", e.getClass().getSimpleName());
            return 3;
        }
    }
}
