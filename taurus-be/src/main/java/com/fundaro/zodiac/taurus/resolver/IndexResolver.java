package com.fundaro.zodiac.taurus.resolver;

import com.fundaro.zodiac.taurus.utils.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Risolve il nome finale dell'indice OpenSearch per una data entità.
 *
 * <p>Il tenant ID viene passato esplicitamente: il chiamante lo legge dal Reactor Context
 * tramite {@code Mono.deferContextual} (nei metodi reattivi) oppure direttamente dal token
 * JWT (nei metodi sincroni come {@code alignChildrenInformation}).
 *
 * <h3>Indici globali</h3>
 * Gli indici globali (es. dati di lookup condivisi tra tutti i tenant) vengono costruiti
 * senza prefisso. Configurazione in {@code application.yml}:
 * <pre>
 * opensearch:
 *   global-indices: tenants, language, currency
 * </pre>
 */
@Service
public class IndexResolver {

    private static final Logger log = LoggerFactory.getLogger(IndexResolver.class);

    private final Set<String> globalIndices;

    public IndexResolver(
        @Value("#{T(java.util.Set).copyOf(" +
            "  T(java.util.Arrays).asList(" +
            "    '${opensearch.global-indices:}'.split(',\\s*')))}")
        Set<String> globalIndices
    ) {
        this.globalIndices = globalIndices.stream()
            .filter(s -> !s.isBlank())
            .collect(Collectors.toUnmodifiableSet());

        log.info("IndexResolver initialised — global (tenant-free) indices: {}", this.globalIndices);
    }

    /**
     * Costruisce il nome dell'indice OpenSearch.
     *
     * @param entityName nome della classe entità (camelCase), es. {@code "AlbumMedia"}
     * @param tenantId   tenant ID estratto dal Reactor Context o dal token JWT; può essere
     *                   {@code null} o vuoto per indici non-tenant
     * @return nome indice risolto, es. {@code "bmcdg-tracks"} oppure {@code "tenants"}
     */
    public String resolve(String entityName, String tenantId) {
        String kebabEntity = Converter.camelCaseToKebabCase(entityName);

        if (globalIndices.contains(kebabEntity)) {
            log.debug("Index '{}' is global — no tenant prefix applied", kebabEntity);
            return kebabEntity;
        }

        if (tenantId == null || tenantId.isBlank()) {
            log.debug("No tenant provided for index '{}' — building without prefix", kebabEntity);
            return kebabEntity;
        }

        String resolved = Converter.camelCaseToKebabCase(Converter.tenantConcatSnakeCase(tenantId, entityName));
        log.debug("Resolved index: '{}'", resolved);
        return resolved;
    }
}
