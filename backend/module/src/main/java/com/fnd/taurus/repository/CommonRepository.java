package com.fnd.taurus.repository;

import com.fnd.taurus.entity.CommonFields;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.PagingAndSortingRepository;

@NoRepositoryBean
public interface CommonRepository<C extends CommonFields> extends PagingAndSortingRepository<C, Long>, JpaSpecificationExecutor<C> {
}
