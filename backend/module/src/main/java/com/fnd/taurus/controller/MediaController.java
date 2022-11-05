package com.fnd.taurus.controller;

import com.fnd.taurus.dto.MediaDTO;
import com.fnd.taurus.entity.Media;
import com.fnd.taurus.repository.MediaRepository;
import com.fnd.taurus.service.MediaService;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequestMapping(value = "media")
@RestController
@Validated
public class MediaController implements CommonController<Media, MediaDTO, MediaRepository, MediaService> {
    private final MediaService mediaService;

    @Autowired
    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Override
    public MediaService getService() {
        return mediaService;
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> stream(@PathVariable("id") Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(mediaService.stream(id, keycloakAuthenticationToken));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MediaDTO> saveFile(@RequestParam MultipartFile file, @RequestParam Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        return ResponseEntity.ok(mediaService.saveFile(file, id, keycloakAuthenticationToken));
    }
}
