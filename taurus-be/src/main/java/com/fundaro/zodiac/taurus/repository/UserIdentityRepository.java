package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.UserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {
    Optional<UserIdentity> findByKeycloakId(String keycloakId);
}
