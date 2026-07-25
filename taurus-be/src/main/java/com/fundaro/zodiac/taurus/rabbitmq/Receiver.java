package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.RabbitMQConfig;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.impl.PdfProcessingService;
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

@Service
@RabbitListener(queues = RabbitMQConfig.queueNameListener)
public class Receiver {

    private final Logger log;

    private final QueueUploadFilesService queueUploadFilesService;

    private final TracksService tracksService;

    private final PdfProcessingService pdfProcessingService;

    private final String basePath;

    public Receiver(
        QueueUploadFilesService queueUploadFilesService,
        TracksService tracksService,
        PdfProcessingService pdfProcessingService,
        ApplicationProperties applicationProperties
    ) {
        this.tracksService = tracksService;
        this.pdfProcessingService = pdfProcessingService;
        this.log = LoggerFactory.getLogger(Receiver.class);
        this.queueUploadFilesService = queueUploadFilesService;
        this.basePath = applicationProperties.getBasePath();
    }

    @RabbitHandler
    public void receive(byte[] message) throws IOException, ClassNotFoundException {
        log.debug("Received message {}", message);
        UploadFilesPackage uploadFilesPackage = (UploadFilesPackage) Converter.bytesToObject(message);
        AbstractAuthenticationToken abstractAuthenticationToken = uploadFilesPackage.getAbstractAuthenticationToken();

        QueueUploadFilesDTO queueUploadFilesDTO = queueUploadFilesService
            .findOne(uploadFilesPackage.getQueueId(), abstractAuthenticationToken).orElse(null);

        if (queueUploadFilesDTO != null) {
            TracksDTO tracksDTO = tracksService
                .findOne(queueUploadFilesDTO.getTrackId(), abstractAuthenticationToken).orElse(null);
            String sourcePath = queueUploadFilesDTO.getPath();
            String type = Strings.isNotBlank(queueUploadFilesDTO.getType()) ? queueUploadFilesDTO.getType() : "unknowns";

            if (tracksDTO != null) {
                if (tracksDTO.getScores() == null) {
                    tracksDTO.setScores(new HashSet<>());
                }

                File file = new File(sourcePath);
                byte[] pdfBytes;
                List<String> filesPath;

                try (InputStream inputStream = new FileInputStream(file)) {
                    String destinationPath = Paths.get(basePath, type, file.getParentFile().getName())
                        .toString().toLowerCase();
                    log.info("Converting pdf2Image file from {} to {}", sourcePath, destinationPath);
                    pdfBytes = inputStream.readAllBytes();
                    filesPath = Converter.pdfToImage(pdfBytes, file.getName(), destinationPath);
                }

                if (!filesPath.isEmpty()) {
                    List<SheetsMusicDTO> sheets =
                        pdfProcessingService.buildSheets(pdfBytes, filesPath, tracksDTO, abstractAuthenticationToken);

                    tracksDTO.getScores().addAll(sheets);
                    tracksDTO = tracksService.update(tracksDTO.getId(), tracksDTO, abstractAuthenticationToken);
                    log.info("Updated track with {} sheet music parts", sheets.size());
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
