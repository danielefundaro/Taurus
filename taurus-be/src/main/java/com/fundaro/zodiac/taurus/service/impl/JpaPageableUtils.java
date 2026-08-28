package com.fundaro.zodiac.taurus.service.impl;

import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class JpaPageableUtils {

    private static final String OPENSEARCH_KEYWORD_SUFFIX = ".keyword";

    private JpaPageableUtils() {}

    static Pageable normalize(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getSort().isUnsorted()) {
            return pageable;
        }

        Sort normalizedSort = Sort.by(
            pageable.getSort().stream().map(order -> order.withProperty(normalizeProperty(order.getProperty()))).toList()
        );
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), normalizedSort);
    }

    static String normalizeProperty(String property) {
        String normalized = property;
        if (normalized.toLowerCase(Locale.ROOT).endsWith(OPENSEARCH_KEYWORD_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - OPENSEARCH_KEYWORD_SUFFIX.length());
        }

        StringBuilder jpaProperty = new StringBuilder(normalized.length());
        boolean capitalizeNext = false;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                jpaProperty.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                jpaProperty.append(character);
            }
        }
        return jpaProperty.toString();
    }
}
