package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsersRepository extends CatalogRepository<Users> {
    Optional<Users> findByKeycloakIdAndDeletedFalse(String keycloakId);

    @Query("""
        select distinct u.keycloakId from Users u
        join u.roles role
        where u.deleted = false
          and u.active = true
          and u.keycloakId is not null
          and role in :roles
        """)
    List<String> findActiveKeycloakIdsByRolesIn(@Param("roles") Collection<RoleEnum> roles);
}
