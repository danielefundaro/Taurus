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

    Optional<E> findByIdAndUserId(Long id, String userId);

    List<E> findAllByUserId(String userId);

    long deleteAllByUserId(String userId);

    long deleteAllByInsertDateBefore(ZonedDateTime cutoff);

    @Modifying
    @Query("UPDATE #{#entityName} e SET e.deleted = TRUE WHERE e.id = :id AND e.userId = :userId")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);
}
