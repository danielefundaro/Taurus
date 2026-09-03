package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pulizia dello storage di un tenant.
 *
 * Filesystem e database non condividono la stessa transazione: una scrittura
 * riuscita seguita da un errore non recuperabile può lasciare un file senza
 * riga corrispondente, e una directory di lavoro può sopravvivere a un
 * processo interrotto. Questo servizio rimuove entrambi i residui, sempre
 * confinato alla directory del tenant e solo oltre un periodo di grazia, così
 * un caricamento in corso non viene mai scambiato per un file orfano.
 *
 * Le righe ancora presenti in {@code media_asset} non vengono mai toccate: la
 * cancellazione logica di un media resta responsabilità del dominio.
 */
@Service
public class MediaStorageCleanupService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageCleanupService.class);

    private final MediaRepository mediaRepository;
    private final TenantStorageService tenantStorageService;
    private final ApplicationProperties.MediaProperties properties;

    public MediaStorageCleanupService(
        MediaRepository mediaRepository,
        TenantStorageService tenantStorageService,
        ApplicationProperties applicationProperties
    ) {
        this.mediaRepository = mediaRepository;
        this.tenantStorageService = tenantStorageService;
        this.properties = applicationProperties.getMedia();
    }

    /** Esegue la pulizia sul tenant corrente. Restituisce il numero di elementi rimossi. */
    @Transactional(readOnly = true)
    public int cleanupCurrentTenant(String tenantCode) {
        int removed = 0;
        try {
            removed += tenantStorageService.deleteStaleTemporaryFiles(tenantCode, Duration.ofHours(properties.getTemporaryFileHours()));
        } catch (RuntimeException exception) {
            log.warn("Unable to purge temporary files of tenant {}", tenantCode, exception);
        }

        try {
            removed += deleteOrphanFiles(tenantCode);
            tenantStorageService.pruneEmptyDirectories(tenantCode);
        } catch (RuntimeException exception) {
            log.warn("Unable to purge orphan files of tenant {}", tenantCode, exception);
        }

        if (removed > 0) log.info("Removed {} stale storage entries for tenant {}", removed, tenantCode);
        return removed;
    }

    private int deleteOrphanFiles(String tenantCode) {
        Duration grace = Duration.ofHours(properties.getOrphanFileHours());
        List<String> candidates = tenantStorageService.listStorageKeysOlderThan(tenantCode, grace);
        if (candidates.isEmpty()) return 0;

        Set<String> known = new HashSet<>(mediaRepository.findActiveStorageKeys());
        int removed = 0;
        for (String storageKey : candidates) {
            if (known.contains(storageKey)) continue;
            try {
                if (tenantStorageService.delete(tenantCode, storageKey)) {
                    removed++;
                    log.debug("Deleted orphan media file {} of tenant {}", storageKey, tenantCode);
                }
            } catch (IOException | IllegalArgumentException exception) {
                log.warn("Unable to delete orphan media file {} of tenant {}", storageKey, tenantCode, exception);
            }
        }
        return removed;
    }
}
