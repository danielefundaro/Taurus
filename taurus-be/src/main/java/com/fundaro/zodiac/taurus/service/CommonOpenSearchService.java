package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

public interface CommonOpenSearchService<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria> {
    /**
     * Save an entity.
     *
     * @param dto the entity to save.
     * @return the persisted entity.
     */
    Mono<D> save(D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Updates an entity.
     *
     * @param id  the id of the entity.
     * @param dto the entity to update.
     * @return the persisted entity.
     */
    Mono<D> update(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Partially updates an entity.
     *
     * @param dto the entity to update partially.
     * @return the persisted entity.
     */
    Mono<D> partialUpdate(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Find entities by criteria.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Mono<Page<D>> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Get the "id" entity.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Delete the "id" entity.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
