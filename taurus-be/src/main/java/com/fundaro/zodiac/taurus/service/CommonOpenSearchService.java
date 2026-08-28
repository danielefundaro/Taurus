package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.util.Optional;

public interface CommonOpenSearchService<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria> {

    D save(D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D update(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D partialUpdate(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    Page<D> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    Optional<D> findOne(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    long count(C criteria, AbstractAuthenticationToken abstractAuthenticationToken);

    D delete(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

}
