package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.criteria.QueueUploadFilesCriteria;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * Service Interface for managing {@link QueueUploadFiles}.
 */
public interface QueueUploadFilesService extends CommonOpenSearchService<QueueUploadFiles, QueueUploadFilesDTO, QueueUploadFilesCriteria> {
    Mono<Mono<QueueUploadFilesDTO>> saveStream(QueueUploadFilesDTO dto, AbstractAuthenticationToken abstractAuthenticationToken);
}
