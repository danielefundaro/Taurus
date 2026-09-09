package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.service.impl.TrackPageImageService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracks/{trackId}/scores/{scoreId}/media/{mediaId}")
public class TrackPageImageResource {

    private final TrackPageImageService service;

    public TrackPageImageResource(TrackPageImageService service) {
        this.service = service;
    }

    @GetMapping("/analysis")
    public ResponseEntity<TrackPageImageDTOs.Analysis> analyze(
        @PathVariable Long trackId,
        @PathVariable Long scoreId,
        @PathVariable Long mediaId,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(service.analyze(trackId, scoreId, mediaId, token));
    }

    @PostMapping("/edits")
    public ResponseEntity<TrackPageImageDTOs.EditResult> edit(
        @PathVariable Long trackId,
        @PathVariable Long scoreId,
        @PathVariable Long mediaId,
        @RequestHeader("Idempotency-Key") UUID requestKey,
        @Valid @RequestBody TrackPageImageDTOs.EditRequest request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(service.edit(trackId, scoreId, mediaId, requestKey, request, token));
    }
}
