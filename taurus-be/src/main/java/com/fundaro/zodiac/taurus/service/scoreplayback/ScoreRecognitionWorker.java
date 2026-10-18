package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersion;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersionOrigin;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersionStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreRecognitionJobRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreVersionRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.EngineOutcomeDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlDocuments.MusicXmlException;
import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlDocuments.PartInfo;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreRecognitionProposal.Candidate;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreVersionWriter.NewVersion;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreVersionWriter.Written;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrClient;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.EngineResult;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.PageImage;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.RecognitionResponse;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Runs a recognition job: takes it, reads the pages of the part, waits for the OMR service outside any transaction, then
 * stores one version per engine that succeeded and makes the proposal current, unless the part plays an approved
 * version. A job is taken by one worker only; a result that arrives after the job was handed to another attempt is
 * discarded.
 */
@Service
public class ScoreRecognitionWorker {

    static final String SYSTEM = "system";
    static final String NO_PAGES = "noPages";
    static final String INTERRUPTED = "interrupted";
    static final String UNEXPECTED = "unexpected";
    private static final String ENGINE_FAILED = "engineFailed";

    private static final Logger log = LoggerFactory.getLogger(ScoreRecognitionWorker.class);

    private final ScoreRecognitionJobRepository jobRepository;
    private final ScoreVersionRepository versionRepository;
    private final TracksRepository tracksRepository;
    private final MediaService mediaService;
    private final OmrClient omrClient;
    private final ScoreVersionWriter writer;
    private final ScoreRecognitionNotifier notifier;
    private final ScorePlaybackMetrics metrics;
    private final TenantTransactionExecutor transactions;
    private final ObjectMapper objectMapper;

    public ScoreRecognitionWorker(
        ScoreRecognitionJobRepository jobRepository,
        ScoreVersionRepository versionRepository,
        TracksRepository tracksRepository,
        MediaService mediaService,
        OmrClient omrClient,
        ScoreVersionWriter writer,
        ScoreRecognitionNotifier notifier,
        ScorePlaybackMetrics metrics,
        TenantTransactionExecutor transactions,
        ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.versionRepository = versionRepository;
        this.tracksRepository = tracksRepository;
        this.mediaService = mediaService;
        this.omrClient = omrClient;
        this.writer = writer;
        this.notifier = notifier;
        this.metrics = metrics;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
    }

    /** The pages read when the job was taken, and the attempt that took it. */
    private record Input(int attempt, List<PageImage> pages) {}

    public void run(String tenant, Long jobId) {
        boolean claimed = transactions.execute(tenant, () -> jobRepository.claim(jobId, ZonedDateTime.now(), SYSTEM) == 1);
        if (!claimed) {
            log.debug("Recognition job {} already taken or finished", jobId);
            return;
        }
        Input input;
        try {
            input = transactions.execute(tenant, () -> input(tenant, jobId));
        } catch (RuntimeException exception) {
            log.error("Pages of recognition job {} not readable", jobId, exception);
            fail(tenant, jobId, null, UNEXPECTED);
            return;
        }
        if (input.pages().isEmpty()) {
            fail(tenant, jobId, input.attempt(), NO_PAGES);
            return;
        }
        RecognitionResponse response;
        try {
            response = omrClient.recognize(input.pages());
        } catch (OmrException exception) {
            fail(tenant, jobId, input.attempt(), exception.getCode());
            return;
        } catch (RuntimeException exception) {
            log.error("Recognition job {} failed unexpectedly", jobId, exception);
            fail(tenant, jobId, input.attempt(), UNEXPECTED);
            return;
        }
        List<EngineResult> results = Objects.requireNonNullElse(response.results(), List.of());
        results.forEach(result -> metrics.engine(result.engine(), result.succeeded(), result.durationMillis()));
        try {
            transactions.execute(tenant, () -> complete(tenant, jobId, input.attempt(), results));
        } catch (RuntimeException exception) {
            log.error("Results of recognition job {} not stored", jobId, exception);
            fail(tenant, jobId, input.attempt(), UNEXPECTED);
        }
    }

    /** Ends an active job with an error; must run in a tenant transaction. */
    void markFailed(ScoreRecognitionJob job, String code) {
        job.setStatus(ScoreRecognitionJobStatus.FAILED);
        job.setErrorCode(code);
        job.setFinishedAt(ZonedDateTime.now());
        job.touchAudit(SYSTEM);
        metrics.job(code);
        notifier.finished(job);
    }

    private void fail(String tenant, Long jobId, Integer attempt, String code) {
        transactions.execute(tenant, () -> {
            ScoreRecognitionJob job = current(jobId, attempt);
            if (job != null) markFailed(job, code);
        });
    }

    private Input input(String tenant, Long jobId) {
        ScoreRecognitionJob job = jobRepository.findByIdAndDeletedFalse(jobId).orElseThrow();
        List<PageImage> pages = part(job)
            .map(part -> part.getMedia().stream().map(page -> page(page, tenant)).toList())
            .orElse(List.of());
        return new Input(job.getAttempts(), pages);
    }

    private PageImage page(Media page, String tenant) {
        MediaService.MediaContent content = mediaService.getContent(page.getId(), tenant);
        return new PageImage(content.fileName(), content.mimeType(), content.bytes());
    }

    private void complete(String tenant, Long jobId, int attempt, List<EngineResult> results) {
        ScoreRecognitionJob job = current(jobId, attempt);
        if (job == null) return;
        Optional<SheetsMusic> found = part(job);
        if (found.isEmpty()) {
            markFailed(job, NO_PAGES);
            return;
        }
        SheetsMusic part = found.orElseThrow();
        List<EngineOutcomeDTO> outcomes = new ArrayList<>();
        List<Candidate> candidates = new ArrayList<>();
        Map<String, ScoreVersion> versions = new HashMap<>();
        for (EngineResult result : results) {
            if (!result.succeeded()) {
                outcomes.add(outcome(result, result.errorCode() == null ? ENGINE_FAILED : result.errorCode(), null));
                continue;
            }
            try {
                Written written = write(tenant, job, part, result);
                versions.put(result.engine(), written.version());
                candidates.add(new Candidate(result.engine(), written.checks()));
                outcomes.add(outcome(result, null, written.version().getId()));
            } catch (MusicXmlException | IllegalArgumentException exception) {
                log.warn("Result of {} for recognition job {} not usable: {}", result.engine(), jobId, exception.getMessage());
                outcomes.add(outcome(result, ENGINE_FAILED, null));
            }
        }

        Optional<Candidate> proposal = ScoreRecognitionProposal.choose(candidates, percussion(part));
        job.setFinishedAt(ZonedDateTime.now());
        job.touchAudit(SYSTEM);
        if (proposal.isPresent()) {
            ScoreVersion chosen = versions.get(proposal.orElseThrow().engine());
            if (!playsApprovedVersion(part)) part.setCurrentScoreVersionId(chosen.getId());
            job.setEnginesJson(json(outcomes.stream().map(item -> proposed(item, chosen.getId())).toList()));
            job.setStatus(ScoreRecognitionJobStatus.DONE);
            job.setErrorCode(null);
            metrics.job("done");
            notifier.finished(job);
        } else {
            job.setEnginesJson(json(outcomes));
            markFailed(job, outcomes.stream().map(EngineOutcomeDTO::errorCode).filter(Objects::nonNull).findFirst().orElse(ENGINE_FAILED));
        }
    }

    private Written write(String tenant, ScoreRecognitionJob job, SheetsMusic part, EngineResult result) {
        Document document = MusicXmlDocuments.read(result.musicXmlBytes());
        List<PartInfo> parts = MusicXmlDocuments.parts(document);
        if (parts.isEmpty()) throw new IllegalArgumentException("score without parts");
        String engine = result.engine().toLowerCase(Locale.ROOT);
        return writer.write(
            new NewVersion(
                part,
                MusicXmlDocuments.singlePart(document, parts.get(0).id()),
                origin(result.engine()),
                "riconoscimento-" + engine,
                null,
                null,
                job.getPagesFingerprint(),
                job.getId(),
                null,
                result.layout(),
                false,
                SYSTEM
            ),
            (content, fileName) -> mediaService.store(content, fileName, MusicXmlDocuments.COMPRESSED_MEDIA_TYPE, ScoreVersionWriter.MEDIA_CATEGORY, tenant, SYSTEM)
        );
    }

    /** The job, if it is still the active attempt that took it. */
    private ScoreRecognitionJob current(Long jobId, Integer attempt) {
        ScoreRecognitionJob job = jobRepository.findByIdAndDeletedFalse(jobId).orElse(null);
        if (job == null || !job.getStatus().isActive() || (attempt != null && job.getAttempts() != attempt)) {
            log.info("Recognition job {} was handed to another attempt: result discarded", jobId);
            return null;
        }
        return job;
    }

    private Optional<SheetsMusic> part(ScoreRecognitionJob job) {
        return tracksRepository.findByIdAndDeletedFalse(job.getTrackId())
            .flatMap(track -> track.getScores().stream().filter(score -> Objects.equals(score.getId(), job.getSheetMusicId())).findFirst());
    }

    private boolean playsApprovedVersion(SheetsMusic part) {
        Long current = part.getCurrentScoreVersionId();
        return current != null && versionRepository.findByIdAndSheetMusicIdAndDeletedFalse(current, part.getId())
            .map(version -> version.getStatus() == ScoreVersionStatus.APPROVED)
            .orElse(false);
    }

    private static boolean percussion(SheetsMusic part) {
        List<Instruments> instruments = part.getInstruments();
        return !instruments.isEmpty() && instruments.stream().allMatch(instrument -> instrument.getPercussionKey() != null);
    }

    private static ScoreVersionOrigin origin(String engine) {
        return ScoreRecognitionProposal.AUDIVERIS.equals(engine) ? ScoreVersionOrigin.OMR_AUDIVERIS : ScoreVersionOrigin.OMR_HOMR;
    }

    private static EngineOutcomeDTO outcome(EngineResult result, String errorCode, Long versionId) {
        return new EngineOutcomeDTO(
            result.engine(),
            result.version(),
            errorCode == null ? ScoreRecognitionJobStatus.DONE.name() : ScoreRecognitionJobStatus.FAILED.name(),
            errorCode,
            result.durationMillis(),
            versionId,
            false
        );
    }

    private static EngineOutcomeDTO proposed(EngineOutcomeDTO outcome, Long chosenId) {
        boolean chosen = chosenId.equals(outcome.versionId());
        return new EngineOutcomeDTO(outcome.engine(), outcome.version(), outcome.status(), outcome.errorCode(), outcome.durationMillis(), outcome.versionId(), chosen);
    }

    private String json(List<EngineOutcomeDTO> outcomes) {
        try {
            return objectMapper.writeValueAsString(outcomes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the engine outcomes", exception);
        }
    }
}
