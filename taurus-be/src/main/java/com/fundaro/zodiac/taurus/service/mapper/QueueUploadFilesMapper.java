package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link QueueUploadFiles} and its DTO {@link QueueUploadFilesDTO}.
 */
@Mapper(componentModel = "spring")
public interface QueueUploadFilesMapper extends EntityOpenSearchMapper<QueueUploadFilesDTO, QueueUploadFiles> {
    @Mapping(target = "path", source = "path")
    @Mapping(target = "status", source = "status")
    QueueUploadFilesDTO toDto(QueueUploadFiles queueUploadFiles);
}
