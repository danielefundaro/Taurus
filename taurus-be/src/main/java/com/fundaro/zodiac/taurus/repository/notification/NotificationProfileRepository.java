package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationProfileRepository extends JpaRepository<NotificationProfile, Long> {

    @EntityGraph(attributePaths = {"categories", "user"})
    Optional<NotificationProfile> findByUserKeycloakIdAndDeletedFalse(String keycloakId);

    @EntityGraph(attributePaths = {"categories", "user"})
    List<NotificationProfile> findAllByUserKeycloakIdInAndDeletedFalse(Collection<String> keycloakIds);

    long deleteAllByUserKeycloakId(String keycloakId);
}
