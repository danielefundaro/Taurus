package com.fundaro.zodiac.taurus.rabbitmq;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.RabbitMQConfig;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.impl.PdfProcessingService;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.utils.Converter;
import com.fundaro.zodiac.taurus.utils.pdf.PdfAnnotations;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@RabbitListener(queues = RabbitMQConfig.queueNameListener)
public class Receiver {

    private final Logger log;
    private final QueueUploadFilesService queueUploadFilesService;
    private final TracksService tracksService;
    private final PdfProcessingService pdfProcessingService;
    private final TenantStorageService tenantStorageService;
    private final ObjectMapper objectMapper;

    public Receiver(
        QueueUploadFilesService queueUploadFilesService,
        TracksService tracksService,
        PdfProcessingService pdfProcessingService,
        TenantStorageService tenantStorageService
    ) {
        this.tracksService = tracksService;
        this.pdfProcessingService = pdfProcessingService;
        this.log = LoggerFactory.getLogger(Receiver.class);
        this.queueUploadFilesService = queueUploadFilesService;
        this.tenantStorageService = tenantStorageService;
        this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @RabbitHandler
    public void receive(byte[] message) throws IOException, ClassNotFoundException {
        UploadFilesPackage uploadFilesPackage = (UploadFilesPackage) Converter.bytesToObject(message);
        AbstractAuthenticationToken token = uploadFilesPackage.getAbstractAuthenticationToken();
        String tenantCode = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("Upload job message does not contain a tenant");
        }
        log.debug("Received upload job message: queueId={}, tenant={}", uploadFilesPackage.getQueueId(), tenantCode);

        try (TenantContext.Scope ignored = TenantContext.use(tenantCode)) {
            process(uploadFilesPackage, token, tenantCode);
        }
    }

    private void process(UploadFilesPackage uploadFilesPackage, AbstractAuthenticationToken token, String tenantCode) throws IOException {
        QueueUploadFilesDTO upload = queueUploadFilesService.findOne(uploadFilesPackage.getQueueId(), token).orElse(null);
        if (upload == null) {
            log.error("Could not find upload job {} in tenant {}", uploadFilesPackage.getQueueId(), tenantCode);
            return;
        }

        TracksDTO track = tracksService.findOne(upload.getTrackId(), token).orElse(null);
        if (track == null) {
            log.error("Could not find track {} in tenant {}", upload.getTrackId(), tenantCode);
            return;
        }
        if (track.getScores() == null) {
            track.setScores(new HashSet<>());
        }

        String sourcePath = upload.getPath();
        String type = Strings.isNotBlank(upload.getType()) ? upload.getType() : "unknowns";
        PdfAnnotations annotations = parseAnnotations(upload.getDescription());
        File file = new File(sourcePath);
        byte[] pdfBytes;
        List<String> filesPath;

        try (InputStream inputStream = new FileInputStream(file)) {
            String destinationPath = tenantStorageService
                .resolve(tenantCode, type, file.getParentFile().getName())
                .toString().toLowerCase();
            log.info("Converting pdf2Image file from {} to {}", sourcePath, destinationPath);
            pdfBytes = inputStream.readAllBytes();
            filesPath = Converter.pdfToImage(pdfBytes, file.getName(), destinationPath, annotations);
        }

        if (filesPath.stream().noneMatch(Objects::nonNull)) {
            log.error("Could not convert any files in {}", sourcePath);
            return;
        }

        List<SheetsMusicDTO> sheets = pdfProcessingService.buildSheets(pdfBytes, filesPath, track, token);
        track.getScores().addAll(sheets);
        tracksService.update(track.getId(), track, token);
        log.info("Updated track with {} sheet music parts", sheets.size());
    }

    private PdfAnnotations parseAnnotations(String description) {
        if (Strings.isBlank(description)) {
            return null;
        }
        try {
            PdfAnnotations annotations = objectMapper.readValue(description, PdfAnnotations.class);
            log.debug(
                "Parsed PDF annotations: {} excluded pages, {} crop regions",
                getSize(annotations.getExcludedPages()),
                getSize(annotations.getCropRegions())
            );
            return annotations;
        } catch (Exception exception) {
            log.warn("Could not parse PDF annotations, processing without: {}", exception.getMessage());
            return null;
        }
    }

    private <T> int getSize(List<T> list) {
        return list == null ? 0 : list.size();
    }
}
