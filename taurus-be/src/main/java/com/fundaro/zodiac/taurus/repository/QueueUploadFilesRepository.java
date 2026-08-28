package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import java.util.List;

public interface QueueUploadFilesRepository extends CatalogRepository<QueueUploadFiles> {
    List<QueueUploadFiles> findAllByUser_KeycloakId(String keycloakId);
}
