package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.criteria.CommonOpenSearchCriteria;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;
import tech.jhipster.service.filter.StringFilter;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface CommonOpenSearchService<E extends CommonFieldsOpenSearch, D extends CommonFieldsOpenSearchDTO, C extends CommonOpenSearchCriteria> {
    /**
     * Save an entity.
     *
     * @param dto the entity to save.
     * @param abstractAuthenticationToken the token of the user.
     * @return the persisted entity.
     */
    Mono<D> save(D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Updates an entity.
     *
     * @param id  the id of the entity.
     * @param dto the entity to update.
     * @param abstractAuthenticationToken the token of the user.
     * @return the persisted entity.
     */
    Mono<D> update(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Partially updates an entity.
     *
     * @param id  the id of the entity.
     * @param dto the entity to update partially.
     * @param abstractAuthenticationToken the token of the user.
     * @return the persisted entity.
     */
    Mono<D> partialUpdate(String id, D dto, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Find entities by criteria.
     *
     * @param criteria filtering criteria.
     * @param pageable the pagination information.
     * @param abstractAuthenticationToken the token of the user.
     * @return the list of entities.
     */
    Mono<Page<D>> findByCriteria(C criteria, Pageable pageable, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Get the "id" entity.
     *
     * @param id the id of the entity.
     * @param abstractAuthenticationToken the token of the user.
     * @return the entity.
     */
    Mono<D> findOne(String id, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Delete the "id" entity.
     *
     * @param id the id of the entity.
     * @param abstractAuthenticationToken the token of the user.
     * @return a Mono to signal the deletion
     */
    Mono<Boolean> delete(String id, AbstractAuthenticationToken abstractAuthenticationToken);

    /**
     * Delete the "childId" entity.
     *
     * @param childId the id of the entity.
     * @param abstractAuthenticationToken the token of the user.
     * @param criteria the criteria to find children information.
     * @param deleteChildFunction the definition of the function.
     */
    void deleteChildInformation(String childId, AbstractAuthenticationToken abstractAuthenticationToken, Function<StringFilter, C> criteria, BiFunction<D, String, Boolean> deleteChildFunction);
}
