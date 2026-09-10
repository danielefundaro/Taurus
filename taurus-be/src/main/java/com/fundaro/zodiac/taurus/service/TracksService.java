package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.TracksCriteria;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Interface for managing {@link Tracks}.
 */
public interface TracksService extends CommonOpenSearchService<Tracks, TracksDTO, TracksCriteria> {
    TracksDTO appendScores(Long id, List<SheetsMusicDTO> scores, AbstractAuthenticationToken abstractAuthenticationToken);

    QueueUploadFilesDTO uploadFile(Long id, MultipartFile file, String annotations, AbstractAuthenticationToken abstractAuthenticationToken);

    List<QueueUploadFilesDTO> findUploadJobs(Long id, AbstractAuthenticationToken abstractAuthenticationToken);

    QueueUploadFilesDTO retryUploadJob(Long id, Long jobId, AbstractAuthenticationToken abstractAuthenticationToken);
}
