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

    /**
     * Quanti utenti hanno assegnato ciascuno degli strumenti indicati.
     * Restituisce una riga per strumento con almeno un utente: gli strumenti
     * senza utenti non compaiono, e il chiamante li tratta come zero.
     */
    @Query("""
        select i.id, count(u.id) from Users u
        join u.instruments i
        where u.deleted = false
          and i.id in :instrumentIds
        group by i.id
        """)
    List<Object[]> countUsersByInstrumentIds(@Param("instrumentIds") Collection<Long> instrumentIds);

    @Query("""
        select distinct u.keycloakId from Users u
        join u.roles role
        where u.deleted = false
          and u.active = true
          and u.keycloakId is not null
          and role in :roles
        """)
    List<String> findActiveKeycloakIdsByRolesIn(@Param("roles") Collection<RoleEnum> roles);

    @Query("""
        select distinct u.keycloakId from Users u
        where u.deleted = false
          and u.active = true
          and u.keycloakId is not null
        """)
    List<String> findAllActiveKeycloakIds();
}
