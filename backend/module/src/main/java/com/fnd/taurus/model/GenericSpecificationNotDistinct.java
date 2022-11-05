package com.fnd.taurus.model;

import com.fnd.taurus.entity.CommonFields;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.criteria.*;

@Slf4j
public class GenericSpecificationNotDistinct<C extends CommonFields> extends GenericSpecification<C> {
    public GenericSpecificationNotDistinct(QueryItem item) {
        super(item);
    }

    @Override
    public Predicate toPredicate(Root<C> root, CriteriaQuery<?> criteriaQuery, CriteriaBuilder builder) {
        // get the path
        Path<?> path = getPath(root, item.getKey());
        // get the value
        Object value = null;
        // set distinct
        criteriaQuery.distinct(false);

        try {
            value = getFixedValue(path, item.getValue());
        } catch (Exception e) {
            log.warn(e.getMessage());
        }

        return processPredicate(builder, path, value);
    }
}