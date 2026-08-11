package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

@NoRepositoryBean
public interface CommonRepository<E extends CommonFields, C extends CommonCriteria>
    extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    Optional<E> findByIdAndUserIdAndTenantCode(Long id, String userId, String tenantCode);

    List<E> findAllByUserIdAndTenantCode(String userId, String tenantCode);

    long deleteAllByUserId(String userId);

    long deleteAllByUserIdAndTenantCode(String userId, String tenantCode);

    long deleteAllByTenantCode(String tenantCode);

    long deleteAllByInsertDateBefore(ZonedDateTime cutoff);

    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = TRUE WHERE e.id = :id AND e.userId = :userId AND e.tenantCode = :tenantCode")
    void deleteByIdAndUserIdAndTenantCode(@Param("id") Long id, @Param("userId") String userId, @Param("tenantCode") String tenantCode);
}
