package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface CatalogRepository<E extends CommonFieldsOpenSearch>
    extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    Optional<E> findByIdAndDeletedFalse(Long id);
}
