package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Users;
import java.util.Optional;

public interface UsersRepository extends CatalogRepository<Users> {
    Optional<Users> findByKeycloakIdAndDeletedFalse(String keycloakId);
}
