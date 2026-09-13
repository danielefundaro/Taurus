package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportRow;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingRowAction;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingIdentityOperationRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void retriesOnlySetupEmailsThatPreviouslyFailed() {
        OnboardingIdentityOperation sent = emailOperation("sent-id");
        OnboardingIdentityOperation failed = emailOperation("failed-id");
        when(operations.findAllByJob_IdOrderByRow_RowNumberDesc(7L)).thenReturn(List.of(sent, failed));
        doThrow(new IllegalStateException("mail unavailable"))
            .doNothing()
            .when(keycloak)
            .sendExecuteActionsEmail(eq("failed-id"), anyList());

        assertThat(service.sendSetupEmails(7L)).isEqualTo(1);
        assertThat(sent.getSetupEmailStatus()).isEqualTo(OnboardingIdentityOperation.SetupEmailStatus.SENT);
        assertThat(failed.getSetupEmailStatus()).isEqualTo(OnboardingIdentityOperation.SetupEmailStatus.FAILED);

        assertThat(service.retryFailedSetupEmails(7L)).isZero();
        verify(keycloak, times(1)).sendExecuteActionsEmail(eq("sent-id"), anyList());
        verify(keycloak, times(2)).sendExecuteActionsEmail(eq("failed-id"), anyList());
        assertThat(failed.getSetupEmailStatus()).isEqualTo(OnboardingIdentityOperation.SetupEmailStatus.SENT);
    }

    private static OnboardingImportRow userRow() {
        OnboardingImportRow row = new OnboardingImportRow();
        row.setAction(OnboardingRowAction.CREATE);
        row.setNormalizedPayload(Map.of("email", "member@example.org"));
        return row;
    }

    private static OnboardingIdentityOperation emailOperation(String keycloakId) {
        OnboardingIdentityOperation operation = new OnboardingIdentityOperation();
        operation.setCreatedByJob(true);
        operation.setKeycloakId(keycloakId);
        operation.setStatus(OnboardingIdentityOperation.Status.APPLIED);
        operation.setSetupEmailStatus(OnboardingIdentityOperation.SetupEmailStatus.PENDING);
        return operation;
    }
}
