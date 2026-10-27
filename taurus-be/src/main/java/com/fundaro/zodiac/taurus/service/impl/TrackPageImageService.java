package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.TrackPageEditReceipt;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TrackPageEditReceiptRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrackPageImageService {

    private static final Logger LOG = LoggerFactory.getLogger(TrackPageImageService.class);

    /**
     * Il rendering gira su un pool dedicato invece che sul thread della richiesta: Java2D non
     * risponde all'interruzione, quindi il solo modo di non tenere occupato il thread servlet oltre
     * il limite è aspettare un {@link Future} e abbandonarlo. Il pool non ha coda e rifiuta quando
     * tutti i posti sono occupati, perché accodare richieste costose dietro a un render lento
     * trasformerebbe un sovraccarico in un'attesa indistinguibile da un blocco.
     */
    private final ThreadPoolExecutor renderer = new ThreadPoolExecutor(
        0,
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        60,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new RendererThreadFactory(),
        new ThreadPoolExecutor.AbortPolicy()
    );

    private final TracksRepository tracksRepository;
    private final MediaRepository mediaRepository;
    private final TrackPageEditReceiptRepository receiptRepository;
    private final MediaService mediaService;
    private final ImageTransformationService imageTransformationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TrackPageImageMetrics metrics;
    private final TenantSchemaNameResolver schemaNameResolver;
    private final long renderTimeoutMillis;

    public TrackPageImageService(
        TracksRepository tracksRepository,
        MediaRepository mediaRepository,
        TrackPageEditReceiptRepository receiptRepository,
        MediaService mediaService,
        ImageTransformationService imageTransformationService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager,
        TrackPageImageMetrics metrics,
        TenantSchemaNameResolver schemaNameResolver,
        ApplicationProperties properties
    ) {
        this.tracksRepository = tracksRepository;
        this.mediaRepository = mediaRepository;
        this.receiptRepository = receiptRepository;
        this.mediaService = mediaService;
        this.imageTransformationService = imageTransformationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
        this.schemaNameResolver = schemaNameResolver;
        this.renderTimeoutMillis = Math.max(1, properties.getMedia().getImageEditTimeoutSeconds()) * 1_000L;
    }

    @PreDestroy
    void stopRenderer() {
        renderer.shutdownNow();
    }

    public TrackPageImageDTOs.Analysis analyze(Long trackId, Long scoreId, Long mediaId, AbstractAuthenticationToken token) {
        requireEditor(token);
        long started = System.nanoTime();
        try {
            PageSource source = transactionTemplate.execute(status -> loadSource(trackId, scoreId, mediaId, token));
            TrackPageImageDTOs.Analysis analysis = rendered(() -> imageTransformationService.analyze(mediaId, source.content()));
            metrics.analysis("done", elapsed(started), (long) analysis.width() * analysis.height());
            LOG.debug(
                "Page image analysis done tenant={} track={} score={} source={} pixels={} suggestions={}",
                tenantTag(token),
                trackId,
                scoreId,
                mediaId,
                (long) analysis.width() * analysis.height(),
                analysis.suggestions().size()
            );
            return analysis;
        } catch (RequestAlertException exception) {
            metrics.analysis(exception.getErrorKey(), elapsed(started), 0);
            LOG.info(
                "Page image analysis failed tenant={} track={} score={} source={} outcome={}",
                tenantTag(token),
                trackId,
                scoreId,
                mediaId,
                exception.getErrorKey()
            );
            throw exception;
        }
    }

    public TrackPageImageDTOs.EditResult edit(
        Long trackId,
        Long scoreId,
        Long mediaId,
        UUID requestKey,
        TrackPageImageDTOs.EditRequest request,
        AbstractAuthenticationToken token
    ) {
        requireEditor(token);
        requireUuidV4(requestKey);
        String actor = requiredActor(token);
        String fingerprint = fingerprint(trackId, scoreId, mediaId, request);
        long started = System.nanoTime();
        AtomicInteger storedAssets = new AtomicInteger();
        try {
            TrackPageImageDTOs.EditResult replay = transactionTemplate.execute(status -> replay(actor, requestKey, fingerprint));
            if (replay != null) {
                metrics.edit("replayed", elapsed(started), 0, 0, replay.media().size());
                logEdit("replayed", token, trackId, scoreId, mediaId, fingerprint, replay);
                return replay;
            }

            PageSource source = transactionTemplate.execute(status -> loadSource(trackId, scoreId, mediaId, token));
            if (!source.mimeType().startsWith("image/")) {
                throw new RequestAlertException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media is not an image", "trackPageImage", "image.unsupported");
            }
            List<byte[]> transformed = rendered(() -> imageTransformationService.transform(source.content(), request));
            recordOperations(request);

            TrackPageImageDTOs.EditResult result;
            try {
                result = transactionTemplate.execute(status ->
                    persistEdit(trackId, scoreId, mediaId, requestKey, request, actor, fingerprint, source.fileName(), transformed, storedAssets, token)
                );
            } catch (DataIntegrityViolationException exception) {
                TrackPageImageDTOs.EditResult concurrentReplay = transactionTemplate.execute(status -> replay(actor, requestKey, fingerprint));
                if (concurrentReplay == null) throw exception;
                result = concurrentReplay;
            }

            metrics.edit("done", elapsed(started), source.content().length, outputBytes(transformed), transformed.size());
            logEdit("done", token, trackId, scoreId, mediaId, fingerprint, result);
            return result;
        } catch (RequestAlertException exception) {
            metrics.edit(exception.getErrorKey(), elapsed(started), 0, 0, 0);
            metrics.storageCompensation(storedAssets.get());
            LOG.info(
                "Page image edit failed tenant={} track={} score={} source={} digest={} outcome={} compensated={}",
                tenantTag(token),
                trackId,
                scoreId,
                mediaId,
                shortDigest(fingerprint),
                exception.getErrorKey(),
                storedAssets.get()
            );
            throw exception;
        } catch (RuntimeException exception) {
            metrics.edit("error", elapsed(started), 0, 0, 0);
            metrics.storageCompensation(storedAssets.get());
            LOG.warn(
                "Page image edit failed tenant={} track={} score={} source={} digest={} compensated={}",
                tenantTag(token),
                trackId,
                scoreId,
                mediaId,
                shortDigest(fingerprint),
                storedAssets.get(),
                exception
            );
            throw exception;
        }
    }

    /**
     * Il rendering ha un limite di tempo: oltre quello la richiesta risponde {@code 503} invece di
     * tenere occupato il thread della richiesta. Il lavoro abbandonato finisce da sé, al più tardi
     * quando il pool si spegne; non lascia righe in sospeso perché avviene fuori da ogni
     * transazione.
     */
    private <T> T rendered(Callable<T> work) {
        Future<T> pending;
        try {
            pending = renderer.submit(work);
        } catch (RejectedExecutionException rejected) {
            throw unavailable("Every renderer slot is busy", "image.busy");
        }
        try {
            return pending.get(renderTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            pending.cancel(true);
            throw unavailable("Image rendering exceeded the time limit", "image.timeout");
        } catch (InterruptedException interrupted) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable("Image rendering was interrupted", "image.timeout");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error error) throw error;
            throw new IllegalStateException("Unable to render the page image", failure.getCause());
        }
    }

    /** Le operazioni della ricetta, non i loro valori: per la diagnosi bastano i tipi. */
    private void recordOperations(TrackPageImageDTOs.EditRequest request) {
        if (request.rotationQuarterTurns() != 0) metrics.operation("rotate");
        if (request.deskewDegrees() != 0) metrics.operation("deskew");
        if (!request.crops().isEmpty()) metrics.operation("crop");
        if (request.grayscale()) metrics.operation("grayscale");
        if (request.brightness() != 0) metrics.operation("brightness");
        if (request.contrast() != 0) metrics.operation("contrast");
        if (request.autoContrast()) metrics.operation("auto_contrast");
        if (request.threshold() != null) metrics.operation("threshold");
    }

    private void logEdit(
        String outcome,
        AbstractAuthenticationToken token,
        Long trackId,
        Long scoreId,
        Long mediaId,
        String fingerprint,
        TrackPageImageDTOs.EditResult result
    ) {
        LOG.info(
            "Page image edit {} tenant={} track={} score={} source={} results={} version={} digest={}",
            outcome,
            tenantTag(token),
            trackId,
            scoreId,
            mediaId,
            result.media().stream().map(ChildrenEntitiesDTO::getIndex).toList(),
            result.trackVersion(),
            shortDigest(fingerprint)
        );
    }

    /** Lo schema è la forma tecnica del tenant: identifica senza nominare l'associazione. */
    private String tenantTag(AbstractAuthenticationToken token) {
        return schemaNameResolver.resolve(SecurityUtils.getTenantIdFromAuthentication(token));
    }

    private static String shortDigest(String fingerprint) {
        return fingerprint.length() <= 12 ? fingerprint : fingerprint.substring(0, 12);
    }

    private static long outputBytes(List<byte[]> transformed) {
        long total = 0;
        for (byte[] image : transformed) total += image.length;
        return total;
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private TrackPageImageDTOs.EditResult persistEdit(
        Long trackId,
        Long scoreId,
        Long mediaId,
        UUID requestKey,
        TrackPageImageDTOs.EditRequest request,
        String actor,
        String fingerprint,
        String sourceFileName,
        List<byte[]> transformed,
        AtomicInteger storedAssets,
        AbstractAuthenticationToken token
    ) {
        TrackPageImageDTOs.EditResult replay = replay(actor, requestKey, fingerprint);
        if (replay != null) return replay;

        Tracks track = requiredTrack(trackId);
        if (!Objects.equals(request.expectedTrackVersion(), track.getEntityVersion())) {
            metrics.conflict("version");
            throw conflict("Track was modified by another request", "version.conflict");
        }
        SheetsMusic score = requiredScore(track, scoreId);
        int mediaIndex = mediaIndex(score, mediaId);
        Media oldMedia = score.getMedia().get(mediaIndex);
        List<MediaDTO> savedMedia = new ArrayList<>(transformed.size());
        List<Media> newMedia = new ArrayList<>(transformed.size());
        for (int index = 0; index < transformed.size(); index++) {
            MediaDTO saved = mediaService.store(
                transformed.get(index),
                editedFilename(sourceFileName, index, transformed.size()),
                "image/png",
                "scores",
                token
            );
            savedMedia.add(saved);
            storedAssets.incrementAndGet();
            newMedia.add(mediaRepository.getReferenceById(saved.getId()));
        }
        List<Media> replacementMedia = new ArrayList<>(score.getMedia());
        replacementMedia.remove(mediaIndex);
        replacementMedia.addAll(mediaIndex, newMedia);

        // Hibernate updates ordered list rows in place. When a replacement adds pages,
        // shifted media can temporarily occur twice and violate the active-relation
        // unique index. Soft-delete the old links first, then recreate the final order.
        score.getMedia().clear();
        tracksRepository.flush();
        score.getMedia().addAll(replacementMedia);
        track.setEditDate(new Date());
        tracksRepository.saveAndFlush(track);

        List<ChildrenEntitiesDTO> media = new ArrayList<>(savedMedia.size());
        for (int index = 0; index < savedMedia.size(); index++) {
            MediaDTO saved = savedMedia.get(index);
            ChildrenEntitiesDTO child = new ChildrenEntitiesDTO();
            child.setIndex(saved.getId());
            child.setName(saved.getName());
            child.setOrder((long) mediaIndex + index + 1);
            media.add(child);
        }
        TrackPageImageDTOs.EditResult result = new TrackPageImageDTOs.EditResult(trackId, track.getEntityVersion(), scoreId, mediaId, media);

        TrackPageEditReceipt receipt = new TrackPageEditReceipt();
        receipt.setRequestKey(requestKey);
        receipt.setRequestedBy(actor);
        receipt.setRequestFingerprint(fingerprint);
        receipt.setTrackId(trackId);
        receipt.setSheetMusicId(scoreId);
        receipt.setSourceMediaId(mediaId);
        receipt.setResultTrackVersion(track.getEntityVersion());
        receipt.setResultMediaJson(serializeMedia(media));
        receiptRepository.saveAndFlush(receipt);

        mediaService.deleteIfUnreferenced(oldMedia.getId(), token);
        return result;
    }

    private PageSource loadSource(Long trackId, Long scoreId, Long mediaId, AbstractAuthenticationToken token) {
        Tracks track = requiredTrack(trackId);
        SheetsMusic score = requiredScore(track, scoreId);
        mediaIndex(score, mediaId);
        MediaService.MediaContent content = mediaService.getContent(mediaId, token);
        return new PageSource(content.fileName(), content.mimeType(), content.bytes());
    }

    private Tracks requiredTrack(Long trackId) {
        return tracksRepository.findByIdAndDeletedFalse(trackId)
            .orElseThrow(() -> notFound("Track image not found"));
    }

    private SheetsMusic requiredScore(Tracks track, Long scoreId) {
        return track.getScores().stream().filter(score -> Objects.equals(score.getId(), scoreId)).findFirst()
            .orElseThrow(() -> notFound("Track image not found"));
    }

    private int mediaIndex(SheetsMusic score, Long mediaId) {
        for (int index = 0; index < score.getMedia().size(); index++) {
            if (Objects.equals(score.getMedia().get(index).getId(), mediaId)) return index;
        }
        throw notFound("Track image not found");
    }

    private TrackPageImageDTOs.EditResult replay(String actor, UUID requestKey, String fingerprint) {
        TrackPageEditReceipt receipt = receiptRepository.findByRequestedByAndRequestKey(actor, requestKey).orElse(null);
        if (receipt == null) return null;
        if (!MessageDigest.isEqual(receipt.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII), fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            metrics.conflict("fingerprint");
            throw conflict("Idempotency key already used for a different request", "image.idempotency.conflict");
        }
        Tracks track = requiredTrack(receipt.getTrackId());
        if (!Objects.equals(track.getEntityVersion(), receipt.getResultTrackVersion())) {
            metrics.conflict("stale");
            throw conflict("Idempotency response is no longer current", "image.idempotency.stale");
        }
        SheetsMusic score = requiredScore(track, receipt.getSheetMusicId());
        List<ChildrenEntitiesDTO> media = deserializeMedia(receipt.getResultMediaJson());
        for (ChildrenEntitiesDTO child : media) child.setOrder((long) mediaIndex(score, child.getIndex()) + 1);
        return new TrackPageImageDTOs.EditResult(receipt.getTrackId(), receipt.getResultTrackVersion(), receipt.getSheetMusicId(), receipt.getSourceMediaId(), media);
    }

    private String fingerprint(Long trackId, Long scoreId, Long mediaId, TrackPageImageDTOs.EditRequest request) {
        try {
            byte[] recipe = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((trackId + "\0" + scoreId + "\0" + mediaId + "\0").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest(recipe));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to fingerprint image edit", exception);
        }
    }

    private static String editedFilename(String fileName, int index, int count) {
        String source = fileName == null || fileName.isBlank() ? "page" : fileName;
        int dot = source.lastIndexOf('.');
        String stem = dot > 0 ? source.substring(0, dot) : source;
        return stem + "-edited" + (count > 1 ? "-" + (index + 1) : "") + ".png";
    }

    private String serializeMedia(List<ChildrenEntitiesDTO> media) {
        try {
            return objectMapper.writeValueAsString(media);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize image edit result", exception);
        }
    }

    private List<ChildrenEntitiesDTO> deserializeMedia(String mediaJson) {
        try {
            return objectMapper.readValue(mediaJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize image edit result", exception);
        }
    }

    private static String requiredActor(AbstractAuthenticationToken token) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        if (actor == null || actor.isBlank())
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identity is required", "trackPageImage", "auth.identity.missing");
        return actor;
    }

    private static void requireEditor(AbstractAuthenticationToken token) {
        boolean allowed = token != null && token.getAuthorities().stream().anyMatch(authority ->
            AuthoritiesConstants.SUPER_ADMIN.equals(authority.getAuthority()) ||
                AuthoritiesConstants.ADMIN.equals(authority.getAuthority()) ||
                AuthoritiesConstants.ARCHIVIST.equals(authority.getAuthority())
        );
        if (!allowed)
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Image editing is not allowed", "trackPageImage", "image.forbidden");
    }

    private static void requireUuidV4(UUID key) {
        if (key == null || key.version() != 4)
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A random UUID v4 idempotency key is required", "trackPageImage", "image.idempotencyKey");
    }

    private static RequestAlertException notFound(String message) {
        return new RequestAlertException(HttpStatus.NOT_FOUND, message, "trackPageImage", "image.notFound");
    }

    private static RequestAlertException conflict(String message, String key) {
        return new RequestAlertException(HttpStatus.CONFLICT, message, "trackPageImage", key);
    }

    private static RequestAlertException unavailable(String message, String key) {
        return new RequestAlertException(HttpStatus.SERVICE_UNAVAILABLE, message, "trackPageImage", key);
    }

    /** Thread con un nome riconoscibile: in un thread dump si vede subito chi sta rendendo. */
    private static final class RendererThreadFactory implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "image-render-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private record PageSource(String fileName, String mimeType, byte[] content) {
    }
}
