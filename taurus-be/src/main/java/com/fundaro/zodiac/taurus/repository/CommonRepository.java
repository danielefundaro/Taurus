package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@NoRepositoryBean
public interface CommonRepository<E extends CommonFields, C extends CommonCriteria> extends ReactiveCrudRepository<E, Long>, CommonRepositoryInternal<E, C> {
    Flux<E> findAllBy(Pageable pageable);

    @Override
    <S extends E> Mono<S> save(S entity);

    @Override
    Flux<E> findAll();

    @Override
    Mono<E> findByIdAndUserIdAndTenantCode(Long id, String userId, String tenantCode);

    @Override
    Mono<Void> deleteByIdAndUserIdAndTenantCode(Long id, String userId, String tenantCode);
}

interface CommonRepositoryInternal<E extends CommonFields, C extends CommonCriteria> {
    <S extends E> Mono<S> save(S entity);

    Flux<E> findAllBy(Pageable pageable);

    Flux<E> findAll();

    Mono<E> findByIdAndUserIdAndTenantCode(Long id, String userId, String tenantCode);

    // this is not supported at the moment because of https://github.com/jhipster/generator-jhipster/issues/18269
    // Flux<E> findAllBy(Pageable pageable, Criteria criteria);
    Flux<E> findByCriteria(C criteria, Pageable page, String userId, String tenantCode);

    Mono<Long> countByCriteria(C criteria, String userId, String tenantCode);

    Mono<Void> deleteByIdAndUserIdAndTenantCode(Long id, String userId, String tenantCode);
}
