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
    Mono<E> findById(Long id);

    @Override
    Mono<Void> deleteById(Long id);
}

interface CommonRepositoryInternal<E extends CommonFields, C extends CommonCriteria> {
    <S extends E> Mono<S> save(S entity);

    Flux<E> findAllBy(Pageable pageable);

    Flux<E> findAll();


    Mono<E> findById(Long id);

    // this is not supported at the moment because of https://github.com/jhipster/generator-jhipster/issues/18269
    // Flux<E> findAllBy(Pageable pageable, Criteria criteria);
    Flux<E> findByCriteria(C criteria, Pageable pageable);

    Mono<Long> countByCriteria(C criteria);

    Mono<Void> deleteById(Long id);
}
