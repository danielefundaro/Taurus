package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingIdentityOperationRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OnboardingIdentitySagaServiceTest {

    private final KeycloakService keycloak = mock(KeycloakService.class);
    private final OnboardingIdentityOperationRepository operations = mock(OnboardingIdentityOperationRepository.class);
    private final OnboardingIdentitySagaService service = new OnboardingIdentitySagaService(keycloak, operations);

    @Test
    void doesNotTreatAKeycloakReadFailureAsAMissingIdentity() {
        OnboardingImportRow row = userRow();
        when(keycloak.getGroupIdByName("tenant-a")).thenReturn("group-id");
        when(operations.findByRow_Id(null)).thenReturn(Optional.empty());
        when(keycloak.getUsers()).thenThrow(new IllegalStateException("Keycloak unavailable"));

        assertThatThrownBy(() -> service.prepare(new OnboardingImportJob(), List.of(row), "tenant-a"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Keycloak unavailable");
        verify(keycloak, never()).saveUser(any());
    }

    @Test
    void reusesAnAlreadyAppliedIdentityOperation() {
        OnboardingImportRow row = userRow();
        OnboardingIdentityOperation operation = new OnboardingIdentityOperation();
        operation.setStatus(OnboardingIdentityOperation.Status.APPLIED);
        operation.setKeycloakId("identity-id");
        when(keycloak.getGroupIdByName("tenant-a")).thenReturn("group-id");
        when(operations.findByRow_Id(null)).thenReturn(Optional.of(operation));

        Map<Long, String> result = service.prepare(new OnboardingImportJob(), List.of(row), "tenant-a");

        assertThat(result).containsEntry(null, "identity-id");
        verify(keycloak, never()).getUsers();
        verify(keycloak, never()).updateUser(any());
    }

    private static OnboardingImportRow userRow() {
        OnboardingImportRow row = new OnboardingImportRow();
        row.setAction(OnboardingRowAction.CREATE);
        row.setNormalizedPayload(Map.of("email", "member@example.org"));
        return row;
    }
}
