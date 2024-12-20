package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.utils.ConverterPdf2Image;
import com.fundaro.zodiac.taurus.web.rest.errors.BadRequestAlertException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Media}.
 */
@Service
@Transactional
public class MediaServiceImpl extends CommonServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper, MediaRepository> implements MediaService {

    public MediaServiceImpl(MediaRepository mediaRepository, MediaMapper mediaMapper) {
        super(mediaRepository, mediaMapper, MediaService.class, Media.class.getSimpleName());
    }

    @Override
    public Flux<Void> uploadFile(Flux<FilePart> filePartFlux, AbstractAuthenticationToken abstractAuthenticationToken) {
        return filePartFlux.publishOn(Schedulers.boundedElastic()).flatMap(multipart -> {
            try {
                File file = Files.createTempFile(multipart.filename(), "").toFile();
                Mono<Void> result = multipart.transferTo(file);
                List<String> files = ConverterPdf2Image.parse(file, "destination"); // Obtains all the pages from the pdf

                file.delete();
                return result;
            } catch (IOException e) {
                return Flux.error(new BadRequestAlertException(e.getMessage(), super.getEntityName(), "file.upload"));
            }
        });
    }

    @Override
    public Mono<Resource> streamFile(Long id, AbstractAuthenticationToken abstractAuthenticationToken) {
        // TODO: return a set of pieces
        return super.findOne(id, abstractAuthenticationToken).map(mediaDTO -> new FileSystemResource(Paths.get(mediaDTO.getName())));
    }
}
