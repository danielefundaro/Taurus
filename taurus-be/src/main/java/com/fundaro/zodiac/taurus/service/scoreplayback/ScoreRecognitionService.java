package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreRecognitionJobRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.EngineOutcomeDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.RecognitionJobDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScorePlaybackSupport.Viewer;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrClient;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Starts the optical music recognition of the parts and reports its progress. Reserved to archivists and administrators
 * by the security configuration; the jobs run asynchronously in {@link ScoreRecognitionWorker}.
 */
@Service
@Transactional
public class ScoreRecognitionService {

    static final Set<ScoreRecognitionJobStatus> ACTIVE = Set.of(ScoreRecognitionJobStatus.QUEUED, ScoreRecognitionJobStatus.RUNNING);
    private static final TypeReference<List<EngineOutcomeDTO>> ENGINES = new TypeReference<>() {};

    private final ScorePlaybackSupport support;
    private final ScoreRecognitionJobRepository jobRepository;
    private final ScoreRecognitionQueue queue;
    private final OmrClient omrClient;
    private final ObjectMapper objectMapper;

    public ScoreRecognitionService(
        ScorePlaybackSupport support,
        ScoreRecognitionJobRepository jobRepository,
        ScoreRecognitionQueue queue,
        OmrClient omrClient,
        ObjectMapper objectMapper
    ) {
        this.support = support;
        this.jobRepository = jobRepository;
        this.queue = queue;
        this.omrClient = omrClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Queues the recognition of one part, or of every part with pages when {@code scoreId} is {@code null}. Parts with a
     * job already queued or running are skipped; the jobs started together end with a single notification.
     */
    public List<RecognitionJobDTO> start(Long trackId, Long scoreId, AbstractAuthenticationToken token) {
        if (!omrClient.isConfigured()) {
            throw new RequestAlertException(HttpStatus.SERVICE_UNAVAILABLE, "OMR service not configured", ScorePlaybackSupport.ENTITY, "scorePlayback.omr.unavailable");
        }
        Tracks track = support.requireTrack(trackId, Viewer.MANAGER);
        List<SheetsMusic> parts = scoreId == null ? track.getScores() : List.of(support.requirePart(track, scoreId));
        List<SheetsMusic> withPages = parts.stream().filter(part -> !part.getMedia().isEmpty()).toList();
        if (withPages.isEmpty()) {
            throw new RequestAlertException(HttpStatus.UNPROCESSABLE_ENTITY, "No pages to recognize", ScorePlaybackSupport.ENTITY, "scorePlayback.recognition.noPages");
        }
        List<SheetsMusic> idle = withPages.stream().filter(part -> !jobRepository.existsBySheetMusicIdAndStatusInAndDeletedFalse(part.getId(), ACTIVE)).toList();
        if (idle.isEmpty()) throw alreadyRunning();

        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        UUID batch = idle.size() > 1 ? UUID.randomUUID() : null;
        List<ScoreRecognitionJob> created = new ArrayList<>();
        for (SheetsMusic part : idle) {
            ScoreRecognitionJob job = new ScoreRecognitionJob();
            job.initializeAudit(actor);
            job.setSheetMusicId(part.getId());
            job.setTrackId(track.getId());
            job.setBatchId(batch);
            job.setPagesFingerprint(ScorePlaybackSupport.pagesFingerprint(part));
            job.setStatus(ScoreRecognitionJobStatus.QUEUED);
            job.setRequestedBy(actor);
            created.add(job);
        }
        try {
            jobRepository.saveAllAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            throw alreadyRunning();
        }
        String tenant = TenantContext.getTenantCode().orElseGet(() -> SecurityUtils.getTenantIdFromAuthentication(token));
        created.forEach(job -> queue.publishAfterCommit(tenant, job.getId()));
        return created.stream().map(this::toDto).toList();
    }

    /** Latest job of each part of the track, to follow the progress. */
    @Transactional(readOnly = true)
    public List<RecognitionJobDTO> latest(Long trackId) {
        Tracks track = support.requireTrack(trackId, Viewer.MANAGER);
        return latestByPart(track.getId()).values().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, ScoreRecognitionJob> latestByPart(Long trackId) {
        Map<Long, ScoreRecognitionJob> latest = new LinkedHashMap<>();
        for (ScoreRecognitionJob job : jobRepository.findAllByTrackIdAndDeletedFalseOrderByIdDesc(trackId)) {
            latest.putIfAbsent(job.getSheetMusicId(), job);
        }
        return latest;
    }

    public boolean isAvailable() {
        return omrClient.isConfigured();
    }

    public RecognitionJobDTO toDto(ScoreRecognitionJob job) {
        return new RecognitionJobDTO(
            job.getId(),
            job.getSheetMusicId(),
            job.getStatus(),
            job.getErrorCode(),
            job.getAttempts(),
            job.getInsertDate(),
            job.getStartedAt(),
            job.getFinishedAt(),
            engines(job)
        );
    }

    private List<EngineOutcomeDTO> engines(ScoreRecognitionJob job) {
        if (job.getEnginesJson() == null) return List.of();
        try {
            return objectMapper.readValue(job.getEnginesJson(), ENGINES);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored engines of recognition job " + job.getId() + " are not readable", exception);
        }
    }

    private static RequestAlertException alreadyRunning() {
        return new RequestAlertException(HttpStatus.CONFLICT, "Recognition already queued or running", ScorePlaybackSupport.ENTITY, "scorePlayback.recognition.alreadyRunning");
    }
}
