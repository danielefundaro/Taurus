package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import tech.jhipster.service.filter.StringFilter;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface CommonOpenSearchService<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria> {

    D save(D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D update(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D partialUpdate(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    Page<D> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    Optional<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken);

    long count(C criteria, AbstractAuthenticationToken abstractAuthenticationToken);

    D delete(String id, AbstractAuthenticationToken abstractAuthenticationToken);

    void alignChildrenInformation(String childId, AbstractAuthenticationToken abstractAuthenticationToken, Function<StringFilter, C> criteriaFunction, BiFunction<D, String, Boolean> function);
}
