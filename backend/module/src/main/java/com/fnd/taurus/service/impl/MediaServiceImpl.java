package com.fnd.taurus.service.impl;

import com.fnd.taurus.dto.MediaDTO;
import com.fnd.taurus.entity.Media;
import com.fnd.taurus.enums.MediaTypeEnum;
import com.fnd.taurus.repository.AuditRepository;
import com.fnd.taurus.repository.MediaRepository;
import com.fnd.taurus.service.MediaService;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class MediaServiceImpl implements MediaService {
    private final MediaRepository mediaRepository;
    private final AuditRepository auditRepository;

    @Value("${basePath}")
    private String basePath;

    @Autowired
    public MediaServiceImpl(MediaRepository mediaRepository, AuditRepository auditRepository) {
        this.mediaRepository = mediaRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public AuditRepository getAuditRepository() {
        return auditRepository;
    }

    @Override
    public MediaRepository getRepository() {
        return mediaRepository;
    }

    @Override
    public Class<Media> getEntity() {
        return Media.class;
    }

    @Override
    public Class<MediaDTO> getDTO() {
        return MediaDTO.class;
    }

    @Override
    public MediaDTO saveFile(MultipartFile file, Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        String date = new SimpleDateFormat("yyyyMMdd").format(new Date());
        MediaDTO mediaDTO = MediaService.super.getDataById(id, keycloakAuthenticationToken);
        MediaTypeEnum currentType = mediaDTO.getType();

        if (currentType == null) {
            currentType = MediaTypeEnum.OTHER;
        }

        Path filePath = Paths.get(currentType.toString().toLowerCase(), date, String.format("%s_%s", UUID.randomUUID(), mediaDTO.getName()));
        Path newFile = Paths.get(basePath, filePath.toString());

        try {
            Files.createDirectories(newFile.getParent());
            Files.write(newFile, file.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            log.error(String.format("Error occurred while saving the file %s into file system", mediaDTO.getName()));
        }

        mediaDTO.setContentType(file.getContentType());
        mediaDTO.setPath(filePath.toString());
        log.info(String.format("Saved file %s into file system on the path %s", mediaDTO.getName(), mediaDTO.getPath()));

        return MediaService.super.saveData(mediaDTO, keycloakAuthenticationToken);
    }

    @Override
    public byte[] stream(Long id, KeycloakAuthenticationToken keycloakAuthenticationToken) {
        MediaDTO mediaDTO = MediaService.super.getDataById(id, keycloakAuthenticationToken);
        byte[] value = new byte[0];

        if (mediaDTO != null && mediaDTO.getPath() != null && !mediaDTO.getPath().isEmpty()) {
            Path path = Paths.get(basePath, mediaDTO.getPath());
            File file = new File(path.toUri());

            if (file.exists()) {
                try {
                    log.info(String.format("Read file from %s", path));
                    value = Files.readAllBytes(file.toPath());
                } catch (IOException e) {
                    log.error(String.format("Something went wrong while reading file from %s", path));
                    e.printStackTrace();
                }
            }
        }

        return value;
    }
}
