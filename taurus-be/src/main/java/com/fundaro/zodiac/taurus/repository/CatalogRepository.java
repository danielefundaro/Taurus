package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface CatalogRepository<E extends CommonFieldsOpenSearch>
    extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {

    Optional<E> findByIdAndDeletedFalse(Long id);
}
