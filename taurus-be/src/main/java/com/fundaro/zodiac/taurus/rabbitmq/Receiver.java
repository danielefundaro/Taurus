package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.RabbitMQConfig;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.*;
import com.fundaro.zodiac.taurus.utils.Converter;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RabbitListener(queues = RabbitMQConfig.queueNameListener)
public class Receiver {

    private final Logger log;

    private final QueueUploadFilesService queueUploadFilesService;

    private final MediaService mediaService;

    private final TracksService tracksService;

    private final String basePath;

    public Receiver(QueueUploadFilesService queueUploadFilesService, MediaService mediaService, TracksService tracksService, ApplicationProperties applicationProperties) {
        this.mediaService = mediaService;
        this.tracksService = tracksService;
        this.log = LoggerFactory.getLogger(Receiver.class);
        this.queueUploadFilesService = queueUploadFilesService;
        this.basePath = applicationProperties.getBasePath();
    }

    @RabbitHandler
    public void receive(byte[] message) throws IOException, ClassNotFoundException {
        log.debug("Received message {}", message);
        UploadFilesPackage uploadFilesPackage = (UploadFilesPackage) Converter.bytesToObject(message);
        AbstractAuthenticationToken abstractAuthenticationToken = uploadFilesPackage.getAbstractAuthenticationToken();

        // Get queue from id and check if exists
        QueueUploadFilesDTO queueUploadFilesDTO = queueUploadFilesService.findOne(uploadFilesPackage.getQueueId(), abstractAuthenticationToken).block();

        if (queueUploadFilesDTO != null) {
            // Get track from id and check if exists
            TracksDTO tracksDTO = tracksService.findOne(queueUploadFilesDTO.getTrackId(), abstractAuthenticationToken).block();
            String sourcePath = queueUploadFilesDTO.getPath();
            String type = Strings.isNotBlank(queueUploadFilesDTO.getType()) ? queueUploadFilesDTO.getType() : "unknowns";

            if (tracksDTO != null) {
                // if the score list doesn't exists, create one
                if (tracksDTO.getScores() == null) {
                    tracksDTO.setScores(new HashSet<>());
                }

                // Get file from path and convert it
                File file = new File(sourcePath);

                // Convert pdf in images for each page
                List<String> filesPath;

                try (InputStream inputStream = new FileInputStream(file)) {
                    String destinationPath = Paths.get(basePath, type, file.getParentFile().getName()).toString().toLowerCase();
                    log.info("Converting pdf2Image file from {} to {}", sourcePath, destinationPath);
                    filesPath = Converter.pdfToImage(inputStream.readAllBytes(), file.getName(), destinationPath);
                }

                if (!filesPath.isEmpty()) {
                    long order = 0L;
                    Set<SheetsMusicDTO> sheetsMusicDTOSet = new HashSet<>();

                    // Save list of media
                    for (String filePath : filesPath) {
                        MediaDTO mediaDTO = new MediaDTO();
                        mediaDTO.setPath(filePath);
                        mediaDTO.setContentType("image/png");
                        mediaDTO.setName(tracksDTO.getName());
                        mediaDTO.setDescription(tracksDTO.getDescription());
                        mediaDTO = mediaService.save(mediaDTO, abstractAuthenticationToken).block();
                        log.info("Saved media entity");

                        // Save scores
                        if (mediaDTO != null) {
                            SheetsMusicDTO sheetsMusicDTO = new SheetsMusicDTO();
                            ChildrenEntitiesDTO childrenEntitiesDTO = new ChildrenEntitiesDTO();
                            childrenEntitiesDTO.setIndex(mediaDTO.getId());
                            childrenEntitiesDTO.setName(mediaDTO.getName());
                            childrenEntitiesDTO.setOrder(1L);

                            sheetsMusicDTO.setMedia(Set.of(childrenEntitiesDTO));
                            sheetsMusicDTO.setOrder(++order);
                            sheetsMusicDTOSet.add(sheetsMusicDTO);
                        }
                    }

                    // Update track
                    tracksDTO.getScores().addAll(sheetsMusicDTOSet);
                    tracksDTO = tracksService.update(tracksDTO.getId(), tracksDTO, abstractAuthenticationToken).block();
                    log.info("Updated {} tracks", tracksDTO);
                } else {
                    log.error("Could not convert any files in {}", sourcePath);
                }
            } else {
                log.error("Could not find tracks for {}", queueUploadFilesDTO.getTrackId());
            }
        } else {
            log.error("Could not find queue upload files for {}", uploadFilesPackage.getQueueId());
        }
    }
}
