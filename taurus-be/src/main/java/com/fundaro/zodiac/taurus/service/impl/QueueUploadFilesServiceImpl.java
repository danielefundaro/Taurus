package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.criteria.QueueUploadFilesCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.mapper.QueueUploadFilesMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class QueueUploadFilesServiceImpl
    extends CommonOpenSearchServiceImpl<QueueUploadFiles, QueueUploadFilesDTO, QueueUploadFilesCriteria, QueueUploadFilesMapper, QueueUploadFilesRepository>
    implements QueueUploadFilesService {

    private final TenantStorageService tenantStorageService;
    private final UsersRepository usersRepository;
    private final TracksRepository tracksRepository;

    public QueueUploadFilesServiceImpl(
        QueueUploadFilesRepository repository,
        QueueUploadFilesMapper mapper,
        TenantStorageService tenantStorageService,
        UsersRepository usersRepository,
        TracksRepository tracksRepository
    ) {
        super(repository, mapper, QueueUploadFilesService.class, QueueUploadFiles.class);
        this.tenantStorageService = tenantStorageService;
        this.usersRepository = usersRepository;
        this.tracksRepository = tracksRepository;
    }

    @Override
    public QueueUploadFilesDTO save(QueueUploadFilesDTO dto, AbstractAuthenticationToken token) {
        if (dto.getId() != null) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A new entity cannot already have an ID", getEntityName(), "id.exists");
        }
        Users user = usersRepository.findByKeycloakIdAndDeletedFalse(SecurityUtils.getUserIdFromAuthentication(token)).orElse(null);
        boolean superAdmin = token.getAuthorities().stream()
            .anyMatch(authority -> AuthoritiesConstants.SUPER_ADMIN.equals(authority.getAuthority()));
        if (user == null && !superAdmin) {
            throw new RequestAlertException(HttpStatus.NOT_FOUND, "Current user not found", getEntityName(), "user.notFound");
        }
        dto.setUserId(user == null ? null : user.getId());
        QueueUploadFiles upload = getMapper().toEntity(dto);
        upload.setUser(user);
        if (dto.getTrackId() != null) {
            Tracks track = tracksRepository.findByIdAndDeletedFalse(dto.getTrackId())
                .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Track not found", getEntityName(), "track.notFound"));
            upload.setTrack(track);
        }
        return saveEntity(upload, token, true);
    }

    @Override
    protected Specification<QueueUploadFiles> buildSpecification(QueueUploadFilesCriteria criteria) {
        return super.buildSpecification(criteria).and((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria == null) return cb.conjunction();
            if (criteria.getUserId() != null) addFilter(predicates, cb, root.join("user").get("id"), criteria.getUserId());
            if (criteria.getTrackId() != null) addFilter(predicates, cb, root.join("track").get("id"), criteria.getTrackId());
            addFilter(predicates, cb, root.get("status"), criteria.getStatus());
            addStringFilter(predicates, cb, root.get("type"), criteria.getType());
            return cb.and(predicates.toArray(Predicate[]::new));
        });
    }

    @Override
    public QueueUploadFilesDTO saveStream(QueueUploadFilesDTO dto, AbstractAuthenticationToken token) {
        try {
            MultipartFile multipartFile = dto.getMultipartFile();
            String originalName = multipartFile.getOriginalFilename();
            String fileName = originalName == null ? "upload" : originalName.replaceAll(" ", "_");
            String tenantCode = SecurityUtils.getTenantIdFromAuthentication(token);
            Path path = tenantStorageService.resolve(
                tenantCode,
                UploadFileStatusEnum.TO_PROCESS.toString().toLowerCase(),
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()),
                fileName
            );
            Files.createDirectories(path.getParent());
            Files.write(path, multipartFile.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            dto.setPath(path.toString());
            dto.setStatus(UploadFileStatusEnum.TO_PROCESS);
            dto.setName(fileName);
            return save(dto, token);
        } catch (IOException exception) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Error occurred while uploading the file", getEntityName(), "file.upload");
        }
    }
}
