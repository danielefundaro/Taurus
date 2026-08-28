package com.fundaro.zodiac.taurus.service.user.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.user.MediaService;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service("LowPermissionsMediaService")
public class MediaServiceImpl implements MediaService {
    private final com.fundaro.zodiac.taurus.service.MediaService delegate;
    public MediaServiceImpl(com.fundaro.zodiac.taurus.service.MediaService delegate) { this.delegate = delegate; }
    public Page<MediaDTO> findEntitiesByCriteria(MediaCriteria criteria, Pageable pageable, AbstractAuthenticationToken token) { return delegate.findEntitiesByCriteria(criteria, pageable, token); }
    public Optional<MediaDTO> findOne(Long id, AbstractAuthenticationToken token) { return delegate.findOne(id, token); }
    public Resource streamFile(Long id, AbstractAuthenticationToken token) { return delegate.streamFile(id, token); }
}
