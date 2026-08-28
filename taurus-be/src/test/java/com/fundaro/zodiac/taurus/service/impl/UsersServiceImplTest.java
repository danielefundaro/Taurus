package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.TenantUserMembershipRepository;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.UserIdentityRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.mapper.UsersMapper;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Role;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class UsersServiceImplTest {

    @Mock UsersRepository usersRepository;
    @Mock UsersMapper usersMapper;
    @Mock KeycloakService keycloakService;
    @Mock TenantsService tenantsService;
    @Mock CalendarEventsService calendarEventsService;
    @Mock DataErasureService dataErasureService;
    @Mock UserIdentityRepository userIdentityRepository;
    @Mock InstrumentsRepository instrumentsRepository;
    @Mock TenantUserMembershipRepository membershipRepository;
    @Mock TenantsRepository tenantsRepository;

    private UsersServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UsersServiceImpl(
            usersRepository,
            usersMapper,
            keycloakService,
            tenantsService,
            calendarEventsService,
            dataErasureService,
            userIdentityRepository,
            instrumentsRepository,
            membershipRepository,
            tenantsRepository
        );
    }

    @Test
    void shouldKeepManagedInstrumentCollectionMutableWhenUpdatingUser() {
        Users entity = new Users();
        entity.setId(2L);
        List<Instruments> managedInstruments = new ArrayList<>();
        managedInstruments.add(new Instruments());
        entity.setInstruments(managedInstruments);

        UsersDTO storedDto = new UsersDTO();
        storedDto.setId(2L);
        storedDto.setKeycloakId("keycloak-user-2");

        ChildrenEntitiesDTO instrumentReference = new ChildrenEntitiesDTO();
        instrumentReference.setIndex(6L);
        instrumentReference.setOrder(1L);
        UsersDTO update = new UsersDTO();
        update.setId(2L);
        update.setRoles(Set.of(RoleEnum.ROLE_ARCHIVIST));
        update.setInstruments(new LinkedHashSet<>(List.of(instrumentReference)));

        User keycloakUpdate = new User();
        User storedKeycloakUser = new User();
        Role archivist = new Role();
        archivist.setName(RoleEnum.ROLE_ARCHIVIST.name());
        Instruments resolvedInstrument = new Instruments();
        resolvedInstrument.setId(6L);

        when(usersRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(entity));
        when(usersMapper.toDto(entity)).thenReturn(storedDto);
        when(usersMapper.toKeycloakUser(update)).thenReturn(keycloakUpdate);
        when(keycloakService.getUser("keycloak-user-2")).thenReturn(storedKeycloakUser);
        when(keycloakService.getGroupIdByName("BMCDG")).thenReturn("group-bmcdg");
        when(keycloakService.getClientRoles()).thenReturn(List.of(archivist));
        when(instrumentsRepository.getReferenceById(6L)).thenReturn(resolvedInstrument);
        when(usersRepository.save(entity)).thenReturn(entity);

        service.update(2L, update, authentication());

        assertThat(entity.getInstruments()).isSameAs(managedInstruments).containsExactly(resolvedInstrument);
        entity.getInstruments().clear();
        assertThat(entity.getInstruments()).isEmpty();
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("actor")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
