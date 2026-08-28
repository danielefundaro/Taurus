package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Tenants;
import java.util.Optional;

public interface TenantsRepository extends CatalogRepository<Tenants> {
    Optional<Tenants> findByCodeAndDeletedFalse(String code);
}
