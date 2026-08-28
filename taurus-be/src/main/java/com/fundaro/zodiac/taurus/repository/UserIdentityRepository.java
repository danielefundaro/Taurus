package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.UserIdentity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByKeycloakId(String keycloakId);
}
