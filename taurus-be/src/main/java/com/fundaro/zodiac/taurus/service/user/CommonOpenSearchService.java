package com.fundaro.zodiac.taurus.service.user;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Optional;

public interface CommonOpenSearchService<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria> {
    /**
     * Find entities by criteria.
     *
     * @param criteria                    filtering criteria.
     * @param pageable                    the pagination information.
     * @param abstractAuthenticationToken the token of the user.
     * @return the list of entities.
     */
    Page<D> findEntitiesByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Get the "id" entity.
     *
     * @param id                          the id of the entity.
     * @param abstractAuthenticationToken the token of the user.
     * @return the entity.
     */
    Optional<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
