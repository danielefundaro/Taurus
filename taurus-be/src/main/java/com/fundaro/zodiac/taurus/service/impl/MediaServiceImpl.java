package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.mapper.MediaMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MediaServiceImpl extends CommonOpenSearchServiceImpl<Media, MediaDTO, MediaCriteria, MediaMapper, MediaRepository>
    implements MediaService {

    public MediaServiceImpl(MediaRepository repository, MediaMapper mapper) {
        super(repository, mapper, MediaService.class, Media.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource streamFile(Long id, AbstractAuthenticationToken token) {
        Media media = getRepository().findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Entity not found", getEntityName(), "id.notFound"));
        return new FileSystemResource(media.getPath());
    }
}
