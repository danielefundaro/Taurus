package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.rabbitmq.Sender;
import com.fundaro.zodiac.taurus.rabbitmq.UploadFilesPackage;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class TracksServiceImpl extends CommonOpenSearchServiceImpl<Tracks, TracksDTO, TracksCriteria, TracksMapper, TracksRepository>
    implements TracksService {

    private final QueueUploadFilesService queueUploadFilesService;
    private final MediaRepository mediaRepository;
    private final InstrumentsRepository instrumentsRepository;
    private final Sender sender;

    public TracksServiceImpl(
        TracksRepository repository,
        TracksMapper mapper,
        QueueUploadFilesService queueUploadFilesService,
        MediaRepository mediaRepository,
        InstrumentsRepository instrumentsRepository,
        Sender sender
    ) {
        super(repository, mapper, TracksService.class, Tracks.class);
        this.queueUploadFilesService = queueUploadFilesService;
        this.mediaRepository = mediaRepository;
        this.instrumentsRepository = instrumentsRepository;
        this.sender = sender;
    }

    @Override
    public TracksDTO save(TracksDTO dto, AbstractAuthenticationToken token) {
        finalizeOrders(dto);
        Tracks entity = getMapper().toEntity(dto);
        entity.setScores(resolveScores(dto, List.of()));
        saveEntity(entity, token, true);
        getRepository().flush();
        return getMapper().toDto(entity);
    }

    @Override
    public TracksDTO update(Long id, TracksDTO dto, AbstractAuthenticationToken token) {
        finalizeOrders(dto);
        Tracks entity = getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        if (dto.getVersion() != null && !dto.getVersion().equals(entity.getEntityVersion())) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Track was modified by another request", getEntityName(), "version.conflict");
        }
        getMapper().partialUpdate(entity, dto);
        List<SheetsMusic> resolvedScores = resolveScores(dto, entity.getScores());
        entity.getScores().clear();
        entity.getScores().addAll(resolvedScores);
        saveEntity(entity, token, false);
        getRepository().flush();
        return getMapper().toDto(entity);
    }

    @Override
    public TracksDTO partialUpdate(Long id, TracksDTO dto, AbstractAuthenticationToken token) {
        return update(id, dto, token);
    }

    @Override
    protected Specification<Tracks> buildSpecification(TracksCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            addStringFilter(predicates, cb, root.get("subName"), criteria.getSubName());
            addStringFilter(predicates, cb, root.get("composer"), criteria.getComposer());
            addStringFilter(predicates, cb, root.get("arranger"), criteria.getArranger());
            addStringFilter(predicates, cb, root.get("tempo"), criteria.getTempo());
            addStringFilter(predicates, cb, root.get("tone"), criteria.getTone());
            addFilter(predicates, cb, root.get("state"), criteria.getState());
            if (criteria.getType() != null) {
                query.distinct(true);
                addStringFilter(predicates, cb, root.join("type"), criteria.getType());
            }
            if (criteria.getInstrumentId() != null || criteria.getMediaId() != null) {
                query.distinct(true);
                var score = root.join("scores");
                if (criteria.getInstrumentId() != null) {
                    addFilter(predicates, cb, score.join("instruments").get("id"), criteria.getInstrumentId());
                }
                if (criteria.getMediaId() != null) {
                    addFilter(predicates, cb, score.join("media").get("id"), criteria.getMediaId());
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public QueueUploadFilesDTO uploadFile(Long id, MultipartFile file, String annotations, AbstractAuthenticationToken token) {
        if (file == null || file.isEmpty()) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "File is empty", getEntityName(), "file.empty");
        }
        QueueUploadFilesDTO upload = new QueueUploadFilesDTO();
        upload.setMultipartFile(file);
        upload.setType(getEntityName());
        upload.setDescription(annotations);
        if (id == null) {
            TracksDTO track = new TracksDTO();
            track.setName(FilenameUtils.removeExtension(file.getOriginalFilename()));
            id = save(track, token).getId();
        } else if (findOne(id, token).isEmpty()) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound");
        }
        upload.setTrackId(id);
        QueueUploadFilesDTO queued = queueUploadFilesService.saveStream(upload, token);
        try {
            byte[] message = Converter.objectToBytes(new UploadFilesPackage(queued.getId(), token));
            sendAfterCommit(message);
        } catch (Exception exception) {
            throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to queue the uploaded file", getEntityName(), "queue.error");
        }
        return queued;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueUploadFilesDTO> findUploadJobs(Long id, AbstractAuthenticationToken token) {
        if (findOne(id, token).isEmpty()) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound");
        }
        return queueUploadFilesService.findByTrackId(id);
    }

    @Override
    public QueueUploadFilesDTO retryUploadJob(Long id, Long jobId, AbstractAuthenticationToken token) {
        if (findOne(id, token).isEmpty()) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound");
        }
        QueueUploadFilesDTO job = queueUploadFilesService.findOne(jobId, token)
            .filter(value -> id.equals(value.getTrackId()))
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Upload job not found", getEntityName(), "upload.notFound"));
        if (job.getStatus() != UploadFileStatusEnum.ERROR) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Only failed upload jobs can be retried", getEntityName(), "upload.notRetryable");
        }
        if (!queueUploadFilesService.transitionStatus(jobId, UploadFileStatusEnum.ERROR, UploadFileStatusEnum.TO_PROCESS, token)) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Upload job state changed", getEntityName(), "upload.notRetryable");
        }
        try {
            sendAfterCommit(Converter.objectToBytes(new UploadFilesPackage(jobId, token)));
        } catch (Exception exception) {
            queueUploadFilesService.transitionStatus(jobId, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.ERROR, token);
            throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to queue the uploaded file", getEntityName(), "queue.error");
        }
        return queueUploadFilesService.findOne(jobId, token).orElseThrow();
    }

    private void sendAfterCommit(byte[] message) {
        if (
            !TransactionSynchronizationManager.isActualTransactionActive() ||
            !TransactionSynchronizationManager.isSynchronizationActive()
        ) {
            sender.send(message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    sender.send(message);
                } catch (RuntimeException exception) {
                    getLogger().error("Unable to publish the committed upload job", exception);
                }
            }
        });
    }

    private List<SheetsMusic> resolveScores(TracksDTO dto, List<SheetsMusic> existingScores) {
        if (dto.getScores() == null) return new ArrayList<>();
        Map<Long, SheetsMusic> existingById = new HashMap<>();
        existingScores.forEach(score -> existingById.put(score.getId(), score));
        return dto.getScores().stream()
            .sorted(Comparator.comparing(score -> score.getOrder() == null ? Long.MAX_VALUE : score.getOrder()))
            .map(score -> resolveScore(score, existingById))
            .toList();
    }

    private SheetsMusic resolveScore(SheetsMusicDTO dto, Map<Long, SheetsMusic> existingById) {
        SheetsMusic score;
        if (dto.getId() == null) {
            score = new SheetsMusic();
        } else {
            score = existingById.remove(dto.getId());
            if (score == null) {
                throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Score does not belong to track", getEntityName(), "score.id.invalid");
            }
        }
        score.setDescription(dto.getDescription());
        score.setNeedsReview(Boolean.TRUE.equals(dto.getNeedsReview()));
        score.setMedia(dto.getMedia() == null ? List.of() : dto.getMedia().stream()
            .sorted(Comparator.comparing(this::orderOf))
            .map(ref -> mediaRepository.getReferenceById(ref.getIndex())).toList());
        score.setInstruments(dto.getInstruments() == null ? List.of() : dto.getInstruments().stream()
            .sorted(Comparator.comparing(this::orderOf))
            .map(ref -> instrumentsRepository.getReferenceById(ref.getIndex())).toList());
        return score;
    }

    private Long orderOf(ChildrenEntitiesDTO ref) {
        return ref.getOrder() == null ? Long.MAX_VALUE : ref.getOrder();
    }

    private void finalizeOrders(TracksDTO dto) {
        if (dto.getScores() == null) return;
        AtomicLong scoreOrder = new AtomicLong();
        dto.getScores().stream().sorted(Comparator.comparing(score -> score.getOrder() == null ? Long.MAX_VALUE : score.getOrder()))
            .forEach(score -> {
                score.setOrder(scoreOrder.incrementAndGet());
                normalizeOrder(score.getMedia());
                normalizeOrder(score.getInstruments());
            });
    }

    private void normalizeOrder(java.util.Set<ChildrenEntitiesDTO> refs) {
        if (refs == null) return;
        AtomicLong order = new AtomicLong();
        refs.stream().sorted(Comparator.comparing(this::orderOf)).forEach(ref -> ref.setOrder(order.incrementAndGet()));
    }
}
