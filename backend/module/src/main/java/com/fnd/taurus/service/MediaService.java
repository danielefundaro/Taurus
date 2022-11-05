package com.fnd.taurus.service;

import com.fnd.taurus.dto.MediaDTO;
import com.fnd.taurus.entity.Media;
import com.fnd.taurus.repository.MediaRepository;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

public interface MediaService extends CommonService<Media, MediaDTO, MediaRepository> {
    MediaDTO saveFile(MultipartFile file, Long id, KeycloakAuthenticationToken keycloakAuthenticationToken);

    byte[] stream(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken);
}
