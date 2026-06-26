package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Transactional
public interface CommonService<E extends CommonFields, D extends CommonFieldsDTO, C extends CommonCriteria> {

    D save(D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D update(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    D partialUpdate(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    Page<D> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    long countByCriteria(C criteria, AbstractAuthenticationToken abstractAuthenticationToken);

    long countAll(AbstractAuthenticationToken abstractAuthenticationToken);

    Optional<D> findOne(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    void delete(Long id, AbstractAuthenticationToken abstractAuthenticationToken);
}
