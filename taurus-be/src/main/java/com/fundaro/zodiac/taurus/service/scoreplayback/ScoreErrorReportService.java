package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReport;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReportClosure;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreErrorReportStatus;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersion;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreErrorReportRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreVersionRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.ErrorReportDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.ErrorReportRequestDTO;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScorePlaybackSupport.Viewer;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Errors heard by the listeners of a part. Every role that can listen to a part can report an error on the version it
 * plays; archivists and administrators are notified, read the reports of the track and close them. Approving another
 * version of the part closes its open reports.
 */
@Service
@Transactional
public class ScoreErrorReportService {

    static final int MAX_MESSAGE_LENGTH = 1000;
    static final int MAX_MEASURE_LENGTH = 16;
    /** Open reports a user can have on the same part, so that a listener cannot flood the archivists with notifications. */
    static final int MAX_OPEN_PER_USER = 10;
    /** Closed reports returned with the open ones, newest first. */
    static final int MAX_CLOSED_LISTED = 50;
    static final String AGGREGATE = "SCORE_ERROR_REPORT";
    static final String CREATED = "SEGNALAZIONE_ERRORE";
    private static final Set<NotificationAudience> MANAGERS = Set.of(
        NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN),
        NotificationAudience.role(RoleEnum.ROLE_ADMIN),
        NotificationAudience.role(RoleEnum.ROLE_ARCHIVIST)
    );

    private final ScorePlaybackSupport support;
    private final ScoreErrorReportRepository reportRepository;
    private final ScoreVersionRepository versionRepository;
    private final UsersRepository usersRepository;
    private final NotificationOutboxPublisher publisher;
    private final ScorePlaybackMetrics metrics;

    public ScoreErrorReportService(
        ScorePlaybackSupport support,
        ScoreErrorReportRepository reportRepository,
        ScoreVersionRepository versionRepository,
        UsersRepository usersRepository,
        NotificationOutboxPublisher publisher,
        ScorePlaybackMetrics metrics
    ) {
        this.support = support;
        this.reportRepository = reportRepository;
        this.versionRepository = versionRepository;
        this.usersRepository = usersRepository;
        this.publisher = publisher;
        this.metrics = metrics;
    }

    /** Reports an error on the version played for the part, with the same visibility rules as listening. */
    public void report(Long trackId, Long scoreId, ErrorReportRequestDTO request, AbstractAuthenticationToken token) {
        Viewer viewer = Viewer.of(token);
        Tracks track = support.requireTrack(trackId, viewer);
        SheetsMusic part = support.requirePart(track, scoreId);
        ScoreVersion version = part.getCurrentScoreVersionId() == null
            ? null
            : versionRepository.findByIdAndSheetMusicIdAndDeletedFalse(part.getCurrentScoreVersionId(), part.getId()).orElse(null);
        if (version == null || (viewer != Viewer.MANAGER && !ScorePlaybackSupport.usableByListeners(version, part))) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Playback not available", ScorePlaybackSupport.ENTITY, "scorePlayback.playback.notAvailable");
        }
        String message = request == null || request.message() == null ? "" : request.message().strip();
        String measure = request == null || request.measureNumber() == null ? "" : request.measureNumber().strip();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH || measure.length() > MAX_MEASURE_LENGTH) {
            throw new RequestAlertException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid error report", ScorePlaybackSupport.ENTITY, "scorePlayback.report.invalid");
        }
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        if (actor == null || actor.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "User identity not available", ScorePlaybackSupport.ENTITY, "auth.identity.missing");
        }
        if (reportRepository.countBySheetMusicIdAndReportedByAndStatusAndDeletedFalse(part.getId(), actor, ScoreErrorReportStatus.OPEN) >= MAX_OPEN_PER_USER) {
            throw new RequestAlertException(HttpStatus.CONFLICT, "Too many open reports on the part", ScorePlaybackSupport.ENTITY, "scorePlayback.report.limitReached")
                .with("maxOpen", MAX_OPEN_PER_USER);
        }

        ScoreErrorReport report = new ScoreErrorReport();
        report.initializeAudit(actor);
        report.setSheetMusicId(part.getId());
        report.setTrackId(track.getId());
        report.setScoreVersionId(version.getId());
        report.setMeasureNumber(measure.isEmpty() ? null : measure);
        report.setMessage(message);
        report.setReportedBy(actor);
        report.setReporterName(truncate(tokenName(token)));
        report.setStatus(ScoreErrorReportStatus.OPEN);
        reportRepository.saveAndFlush(report);
        metrics.errorReport("created", 1);
        notifyManagers(report, track, report.getReporterName() == null ? actor : report.getReporterName());
    }

    /** Reports of the parts of the track: the open ones, then the most recent closed ones. */
    @Transactional(readOnly = true)
    public List<ErrorReportDTO> list(Long trackId) {
        Tracks track = support.requireTrack(trackId, Viewer.MANAGER);
        List<Long> partIds = track.getScores().stream().map(SheetsMusic::getId).toList();
        if (partIds.isEmpty()) return List.of();
        List<ScoreErrorReport> all = reportRepository.findAllBySheetMusicIdInAndDeletedFalseOrderByIdDesc(partIds);
        List<ScoreErrorReport> reports = Stream.concat(
            all.stream().filter(report -> report.getStatus() == ScoreErrorReportStatus.OPEN),
            all.stream().filter(report -> report.getStatus() == ScoreErrorReportStatus.CLOSED).limit(MAX_CLOSED_LISTED)
        ).toList();
        return toDtos(reports);
    }

    /** Closes a report of the track; closing a closed report changes nothing. */
    public ErrorReportDTO close(Long trackId, Long reportId, AbstractAuthenticationToken token) {
        Tracks track = support.requireTrack(trackId, Viewer.MANAGER);
        ScoreErrorReport report = reportRepository.findByIdAndTrackIdAndDeletedFalse(reportId, track.getId())
            .filter(found -> track.getScores().stream().anyMatch(part -> Objects.equals(part.getId(), found.getSheetMusicId())))
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Error report not found", ScorePlaybackSupport.ENTITY, "scorePlayback.report.notFound"));
        if (report.getStatus() == ScoreErrorReportStatus.OPEN) {
            report.close(ScoreErrorReportClosure.MANUAL, actor(token));
            metrics.errorReport("closed", 1);
        }
        return toDtos(List.of(report)).get(0);
    }

    /** The approval of a version answers the reports made on the other versions of the part. */
    public void closeForApproval(SheetsMusic part, Long approvedVersionId, String actor) {
        List<ScoreErrorReport> open = reportRepository.findAllBySheetMusicIdAndStatusAndDeletedFalse(part.getId(), ScoreErrorReportStatus.OPEN).stream()
            .filter(report -> !Objects.equals(report.getScoreVersionId(), approvedVersionId))
            .toList();
        open.forEach(report -> report.close(ScoreErrorReportClosure.APPROVAL, actor));
        if (!open.isEmpty()) metrics.errorReport("approved", open.size());
    }

    /** Open reports of each part, for the playback overview. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> openCounts(Collection<Long> partIds) {
        if (partIds.isEmpty()) return Map.of();
        Map<Long, Integer> counts = new HashMap<>();
        for (Object[] row : reportRepository.countOpenBySheetMusicIds(partIds)) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).intValue());
        }
        return counts;
    }

    private List<ErrorReportDTO> toDtos(List<ScoreErrorReport> reports) {
        if (reports.isEmpty()) return List.of();
        Set<Long> versionIds = reports.stream().map(ScoreErrorReport::getScoreVersionId).collect(Collectors.toSet());
        Map<Long, Integer> versionNumbers = versionRepository.findAllById(versionIds).stream()
            .collect(Collectors.toMap(ScoreVersion::getId, ScoreVersion::getVersionNumber));
        Set<String> userIds = new HashSet<>();
        for (ScoreErrorReport report : reports) {
            userIds.add(report.getReportedBy());
            if (report.getClosedBy() != null) userIds.add(report.getClosedBy());
        }
        Map<String, String> names = new HashMap<>();
        for (Users user : usersRepository.findAllByKeycloakIdIn(userIds)) {
            String name = fullName(user);
            if (name != null) names.putIfAbsent(user.getKeycloakId(), name);
        }
        return reports.stream()
            .sorted(Comparator.comparing((ScoreErrorReport report) -> report.getStatus() != ScoreErrorReportStatus.OPEN).thenComparing(ScoreErrorReport::getId, Comparator.reverseOrder()))
            .map(report -> new ErrorReportDTO(
                report.getId(),
                report.getSheetMusicId(),
                report.getScoreVersionId(),
                versionNumbers.get(report.getScoreVersionId()),
                report.getMeasureNumber(),
                report.getMessage(),
                names.getOrDefault(report.getReportedBy(), report.getReporterName()),
                report.getInsertDate(),
                report.getStatus(),
                report.getClosure(),
                report.getClosedBy() == null ? null : names.get(report.getClosedBy()),
                report.getClosedAt()
            ))
            .toList();
    }

    private void notifyManagers(ScoreErrorReport report, Tracks track, String reporterName) {
        String trackName = track.getName() == null || track.getName().isBlank() ? "Traccia senza nome" : track.getName();
        String where = ScoreRecognitionNotifier.partLabel(track, report.getSheetMusicId()) + " · " + trackName
            + (report.getMeasureNumber() == null ? "" : ", battuta " + report.getMeasureNumber());
        publisher.enqueue(
            new NotificationCommand(
                NotificationEventKey.deterministic("score-error-report", report.getId()),
                NotificationSource.CONTENT,
                AGGREGATE,
                String.valueOf(report.getId()),
                CREATED,
                "Segnalato un errore nella parte",
                truncate(where + ": " + report.getMessage()),
                NotificationSeverity.INFO,
                NotificationPreferencePolicy.CONFIGURABLE,
                "/tracks/" + track.getId() + "?tab=playback",
                report.getReportedBy(),
                reporterName,
                MANAGERS,
                null
            )
        );
    }

    private static String fullName(Users user) {
        String value = Stream.of(user.getName(), user.getLastName()).filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty())
            .collect(Collectors.joining(" "));
        return value.isEmpty() ? null : value;
    }

    /** Name and surname of the authenticated user, {@code null} when the token does not carry them. */
    private static String tokenName(AbstractAuthenticationToken token) {
        String value = Stream.of(SecurityUtils.getFirstNameFromAuthentication(token), SecurityUtils.getLastNameFromAuthentication(token))
            .filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty())
            .collect(Collectors.joining(" "));
        return value.isEmpty() ? null : value;
    }

    private static String actor(AbstractAuthenticationToken token) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    private static String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 255 ? value : value.substring(0, 254) + "…";
    }
}
