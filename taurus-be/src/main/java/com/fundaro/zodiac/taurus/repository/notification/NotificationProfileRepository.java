package com.fundaro.zodiac.taurus.repository.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationProfileRepository extends JpaRepository<NotificationProfile, Long> {

    @EntityGraph(attributePaths = {"categories", "user"})
    Optional<NotificationProfile> findByKeycloakSubjectAndDeletedFalse(String keycloakId);

    @EntityGraph(attributePaths = {"categories", "user"})
    List<NotificationProfile> findAllByKeycloakSubjectInAndDeletedFalse(Collection<String> keycloakIds);

    long deleteAllByKeycloakSubject(String keycloakId);
}
