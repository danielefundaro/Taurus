package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.TrackPageEditReceipt;
import com.fundaro.zodiac.taurus.domain.Tracks;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrackPageImageService {

    private final TracksRepository tracksRepository;
    private final MediaRepository mediaRepository;
    private final TrackPageEditReceiptRepository receiptRepository;
    private final MediaService mediaService;
    private final ImageTransformationService imageTransformationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TrackPageImageService(
        TracksRepository tracksRepository,
        MediaRepository mediaRepository,
        TrackPageEditReceiptRepository receiptRepository,
        MediaService mediaService,
        ImageTransformationService imageTransformationService,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.tracksRepository = tracksRepository;
        this.mediaRepository = mediaRepository;
        this.receiptRepository = receiptRepository;
        this.mediaService = mediaService;
        this.imageTransformationService = imageTransformationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TrackPageImageDTOs.Analysis analyze(Long trackId, Long scoreId, Long mediaId, AbstractAuthenticationToken token) {
        requireEditor(token);
        PageSource source = transactionTemplate.execute(status -> loadSource(trackId, scoreId, mediaId, token));
        return imageTransformationService.analyze(mediaId, source.content());
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
        TrackPageImageDTOs.EditResult replay = transactionTemplate.execute(status -> replay(actor, requestKey, fingerprint));
        if (replay != null) return replay;

        PageSource source = transactionTemplate.execute(status -> loadSource(trackId, scoreId, mediaId, token));
        if (!source.mimeType().startsWith("image/")) {
            throw new RequestAlertException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media is not an image", "trackPageImage", "image.unsupported");
        }
        List<byte[]> transformed = imageTransformationService.transform(source.content(), request);

        try {
            return transactionTemplate.execute(status -> persistEdit(trackId, scoreId, mediaId, requestKey, request, actor, fingerprint, source.fileName(), transformed, token));
        } catch (DataIntegrityViolationException exception) {
            TrackPageImageDTOs.EditResult concurrentReplay = transactionTemplate.execute(status -> replay(actor, requestKey, fingerprint));
            if (concurrentReplay != null) return concurrentReplay;
            throw exception;
        }
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
        AbstractAuthenticationToken token
    ) {
        TrackPageImageDTOs.EditResult replay = replay(actor, requestKey, fingerprint);
        if (replay != null) return replay;

        Tracks track = requiredTrack(trackId);
        if (!Objects.equals(request.expectedTrackVersion(), track.getEntityVersion())) {
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
            throw conflict("Idempotency key already used for a different request", "image.idempotency.conflict");
        }
        Tracks track = requiredTrack(receipt.getTrackId());
        if (!Objects.equals(track.getEntityVersion(), receipt.getResultTrackVersion())) {
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
            return objectMapper.readValue(mediaJson, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize image edit result", exception);
        }
    }

    private static String requiredActor(AbstractAuthenticationToken token) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        if (actor == null || actor.isBlank()) throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identity is required", "trackPageImage", "identity.missing");
        return actor;
    }

    private static void requireEditor(AbstractAuthenticationToken token) {
        boolean allowed = token != null && token.getAuthorities().stream().anyMatch(authority ->
            AuthoritiesConstants.SUPER_ADMIN.equals(authority.getAuthority()) ||
            AuthoritiesConstants.ADMIN.equals(authority.getAuthority()) ||
            AuthoritiesConstants.ARCHIVIST.equals(authority.getAuthority())
        );
        if (!allowed) throw new RequestAlertException(HttpStatus.FORBIDDEN, "Image editing is not allowed", "trackPageImage", "image.forbidden");
    }

    private static void requireUuidV4(UUID key) {
        if (key == null || key.version() != 4) throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A random UUID v4 idempotency key is required", "trackPageImage", "image.idempotencyKey");
    }

    private static RequestAlertException notFound(String message) { return new RequestAlertException(HttpStatus.NOT_FOUND, message, "trackPageImage", "image.notFound"); }
    private static RequestAlertException conflict(String message, String key) { return new RequestAlertException(HttpStatus.CONFLICT, message, "trackPageImage", key); }
    private record PageSource(String fileName, String mimeType, byte[] content) {}
}
