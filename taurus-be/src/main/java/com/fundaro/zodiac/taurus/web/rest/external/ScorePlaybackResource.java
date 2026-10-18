package com.fundaro.zodiac.taurus.web.rest.external;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.ErrorReportRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.TrackPlaybackDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreErrorReportService;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScorePlaybackService;
import com.fundaro.zodiac.taurus.web.rest.RequiresTenantFeature;
import com.fundaro.zodiac.taurus.web.rest.ScorePlaybackFiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Score playback of a track for external users: public tracks only. */
@RestController("ExternalScorePlaybackResource")
@RequestMapping("/api/external/tracks/{trackId}")
@RequiresTenantFeature(TenantFeature.SCORE_PLAYBACK)
public class ScorePlaybackResource {

    private final ScorePlaybackService playbackService;
    private final ScoreErrorReportService reportService;

    public ScorePlaybackResource(ScorePlaybackService playbackService, ScoreErrorReportService reportService) {
        this.playbackService = playbackService;
        this.reportService = reportService;
    }

    @GetMapping("/playback")
    public ResponseEntity<TrackPlaybackDTO> playback(@PathVariable Long trackId, AbstractAuthenticationToken token) {
        return ResponseEntity.ok(playbackService.overview(trackId, token));
    }

    @GetMapping("/scores/{scoreId}/playback-file")
    public ResponseEntity<byte[]> playbackFile(@PathVariable Long trackId, @PathVariable Long scoreId, AbstractAuthenticationToken token) {
        return ScorePlaybackFiles.musicXml(playbackService.playbackFile(trackId, scoreId, token));
    }

    @PostMapping("/scores/{scoreId}/error-reports")
    public ResponseEntity<Void> reportError(
        @PathVariable Long trackId,
        @PathVariable Long scoreId,
        @RequestBody ErrorReportRequestDTO request,
        AbstractAuthenticationToken token
    ) {
        reportService.report(trackId, scoreId, request, token);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
