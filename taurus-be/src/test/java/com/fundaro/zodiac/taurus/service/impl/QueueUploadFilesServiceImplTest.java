package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.QueueUploadFiles;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.mapper.QueueUploadFilesMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class QueueUploadFilesServiceImplTest {

    @Mock QueueUploadFilesRepository repository;
    @Mock QueueUploadFilesMapper mapper;
    @Mock TenantStorageService tenantStorageService;
    @Mock UsersRepository usersRepository;
    @Mock TracksRepository tracksRepository;

    private QueueUploadFilesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QueueUploadFilesServiceImpl(repository, mapper, tenantStorageService, usersRepository, tracksRepository);
    }

    @Test
    void savesNewUploadWithManagedRelationshipsAndUninitializedVersion() {
        Users user = new Users();
        user.setId(10L);
        user.setEntityVersion(4L);
        Tracks track = new Tracks();
        track.setId(2L);
        track.setEntityVersion(3L);
        QueueUploadFiles upload = new QueueUploadFiles();
        QueueUploadFilesDTO request = new QueueUploadFilesDTO();
        request.setName("score.pdf");
        request.setStatus(UploadFileStatusEnum.TO_PROCESS);
        request.setTrackId(2L);
        QueueUploadFilesDTO response = new QueueUploadFilesDTO();

        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));
        when(tracksRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(track));
        when(mapper.toEntity(request)).thenReturn(upload);
        when(repository.save(upload)).thenReturn(upload);
        when(mapper.toDto(upload)).thenReturn(response);

        assertThat(service.save(request, authentication())).isSameAs(response);

        assertThat(upload.getUser()).isSameAs(user);
        assertThat(upload.getTrack()).isSameAs(track);
        assertThat(upload.getEntityVersion()).isNull();
        verify(repository).save(same(upload));
    }

    @Test
    void savesSuperAdminUploadWithoutTenantUser() {
        QueueUploadFiles upload = new QueueUploadFiles();
        QueueUploadFilesDTO request = new QueueUploadFilesDTO();
        request.setName("score.pdf");
        request.setStatus(UploadFileStatusEnum.TO_PROCESS);
        QueueUploadFilesDTO response = new QueueUploadFilesDTO();

        when(usersRepository.findByKeycloakIdAndDeletedFalse("super-admin-1")).thenReturn(Optional.empty());
        when(mapper.toEntity(request)).thenReturn(upload);
        when(repository.save(upload)).thenReturn(upload);
        when(mapper.toDto(upload)).thenReturn(response);

        assertThat(service.save(request, authentication("super-admin-1", AuthoritiesConstants.SUPER_ADMIN))).isSameAs(response);

        assertThat(request.getUserId()).isNull();
        assertThat(upload.getUser()).isNull();
        assertThat(upload.getInsertBy()).isEqualTo("super-admin-1");
        verify(repository).save(same(upload));
    }

    @Test
    void rejectsNonSuperAdminWithoutTenantUser() {
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(new QueueUploadFilesDTO(), authentication("user-2", "ROLE_ADMIN")))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Current user not found");
    }

    private JwtAuthenticationToken authentication() {
        return authentication("user-1", null);
    }

    private JwtAuthenticationToken authentication(String subject, String authority) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return authority == null
            ? new JwtAuthenticationToken(jwt)
            : new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }
}
