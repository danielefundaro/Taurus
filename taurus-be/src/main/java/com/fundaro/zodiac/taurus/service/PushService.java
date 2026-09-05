package com.fundaro.zodiac.taurus.service;

import java.util.List;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;

public interface PushService {

    void sendToUser(String userId, String tenantCode, String title, String body);

    void sendToUsers(List<String> userIds, String tenantCode, String title, String body);

    PushDeliveryResult sendToUserNow(String userId, String tenantCode, String title, String body, String targetPath);
}
