package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import com.fundaro.zodiac.taurus.domain.criteria.CommonCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Transactional
public interface CommonService<E extends CommonFields, D extends CommonFieldsDTO, C extends CommonCriteria> {
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
    Mono<D> update(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Partially updates an entity.
     *
     * @param dto the entity to update partially.
     * @return the persisted entity.
     */
    Mono<D> partialUpdate(Long id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Find entities by criteria.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Flux<D> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Find the count of entities by criteria.
     *
     * @param criteria filtering criteria
     * @return the count of entities
     */
    public Mono<Long> countByCriteria(C criteria, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities in the database.
     */
    Mono<Long> countAll(AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Get the "id" entity.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Mono<D> findOne(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Delete the "id" entity.
     *
     * @param id the id of the entity.
     * @return a Mono to signal the deletion
     */
    Mono<Void> delete(Long id, AbstractAuthenticationToken abstractAuthenticationToken);
}
