package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureRequest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryErasureRequestRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanSheetService;
import com.fundaro.zodiac.taurus.service.signature.SignatureErasureService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * La pratica GDPR con materiale inventario si chiude solo a riconsegna completa: allora assegnazioni e segnalazioni di
 * guasto passano allo stesso pseudonimo, così lo storico tecnico del bene resta leggibile senza l'identità del socio.
 */
class InventoryErasureServiceTest {
    private final InventoryErasureRequestRepository requests = mock(InventoryErasureRequestRepository.class);
    private final InventoryAssignmentRepository assignments = mock(InventoryAssignmentRepository.class);
    private final InventoryIssueReportRepository issues = mock(InventoryIssueReportRepository.class);
    private final DataErasureService dataErasure = mock(DataErasureService.class);
    private final KeycloakService keycloak = mock(KeycloakService.class);
    private final InventoryErasureService service = new InventoryErasureService(requests, assignments, dataErasure, keycloak, issues,
        mock(InventoryLoanSheetService.class), mock(SignatureErasureService.class));
    private final InventoryErasureRequest request = new InventoryErasureRequest();

    @BeforeEach
    void setUp() {
        request.setUserKeycloakId("member-1");
        request.setStatus(InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION);
        when(requests.findById(5L)).thenReturn(Optional.of(request));
        when(requests.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycloak.getUserGroups("member-1")).thenReturn(List.of());
    }

    @Test
    void theReporterOfTheIssuesTakesTheSamePseudonymAsTheAssignments() {
        service.complete(5L, authentication());

        ArgumentCaptor<String> pseudonym = ArgumentCaptor.forClass(String.class);
        verify(assignments).pseudonymizeUser(eq("member-1"), pseudonym.capture());
        verify(issues).pseudonymizeUser("member-1", pseudonym.getValue());
        assertThat(pseudonym.getValue()).startsWith("erased:").doesNotContain("member-1");
        verify(dataErasure).eraseUserData("member-1", "tenant-a");
        assertThat(request.getStatus()).isEqualTo(InventoryErasureStatus.COMPLETED);
        assertThat(request.getResolvedBy()).isEqualTo("admin-1");
    }

    @Test
    void outstandingMaterialKeepsTheRequestOpenAndTheIdentityUntouched() {
        when(assignments.hasOutstanding(eq("member-1"), any())).thenReturn(true);

        assertThatThrownBy(() -> service.complete(5L, authentication()))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.erasure.outstanding"));

        verify(assignments, never()).pseudonymizeUser(anyString(), anyString());
        verify(issues, never()).pseudonymizeUser(anyString(), anyString());
        verifyNoInteractions(dataErasure, keycloak);
        assertThat(request.getStatus()).isEqualTo(InventoryErasureStatus.PENDING_INVENTORY_RESOLUTION);
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "admin-1", "tenant", "tenant-a"));
        return new JwtAuthenticationToken(jwt);
    }
}
