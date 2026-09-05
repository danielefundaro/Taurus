package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OnboardingIdentitySagaService {
    private final KeycloakService keycloak;
    private final OnboardingIdentityOperationRepository operations;

    public OnboardingIdentitySagaService(KeycloakService keycloak, OnboardingIdentityOperationRepository operations) {
        this.keycloak = keycloak; this.operations = operations;
    }

    public Map<Long, String> prepare(OnboardingImportJob job, List<OnboardingImportRow> userRows, String tenantCode) {
        Map<Long, String> identities = new HashMap<>();
        String groupId = keycloak.getGroupIdByName(tenantCode);
        for (OnboardingImportRow row : userRows) {
            if (row.getAction() == OnboardingRowAction.SKIP) continue;
            Optional<OnboardingIdentityOperation> previousOperation = operations.findByRow_Id(row.getId());
            if (previousOperation.filter(operation -> operation.getStatus() == OnboardingIdentityOperation.Status.APPLIED).isPresent()) {
                identities.put(row.getId(), previousOperation.orElseThrow().getKeycloakId());
                continue;
            }
            String email = value(row, "email"); String id = find(email);
            boolean created = previousOperation.map(OnboardingIdentityOperation::isCreatedByJob).orElse(id == null);
            OnboardingIdentityOperation operation = previousOperation.orElseGet(OnboardingIdentityOperation::new);
            operation.setJob(job); operation.setRow(row);
            operation.setOperationType(created ? OnboardingIdentityOperation.Type.CREATE : OnboardingIdentityOperation.Type.LINK_EXISTING);
            operation.setCreatedByJob(created);
            if (created && id == null) {
                operations.save(operation);
                User user = new User(); user.setUsername(email); user.setEmail(email); user.setFirstName(value(row, "nome")); user.setLastName(value(row, "cognome"));
                user.setEnabled("SI".equals(value(row, "attivo"))); user.setEmailVerified(false); keycloak.saveUser(user); id = keycloak.getUserIdByUsernameOrEmail(email, email);
            }
            User current = keycloak.getUser(id); List<Group> groups = keycloak.getUserGroups(id);
            boolean alreadyInGroup = groups.stream().anyMatch(group -> tenantCode.equals(group.getName())); operation.setPreviouslyInGroup(alreadyInGroup);
            Map<String, List<String>> attributes = current.getAttributes() == null ? new HashMap<>() : new HashMap<>(current.getAttributes());
            List<String> previous = attributes.get(tenantCode + "_roles"); operation.setPreviousRoles(previous == null ? null : String.join("|", previous));
            operation.setKeycloakId(id); operations.save(operation);
            try {
                if (!alreadyInGroup) keycloak.updateUserGroup(id, groupId);
                attributes.put(tenantCode + "_roles", split(value(row, "ruoli")).stream().map(role -> "ROLE_" + role).toList());
                current.setAttributes(attributes); keycloak.updateUser(current); operation.setStatus(OnboardingIdentityOperation.Status.APPLIED); operations.save(operation); identities.put(row.getId(), id);
            } catch (RuntimeException exception) { operation.setLastErrorCode("KEYCLOAK_PREPARE_FAILED"); operations.save(operation); throw exception; }
        }
        return identities;
    }

    public boolean compensate(Long jobId, String tenantCode) {
        boolean success = true; String groupId = keycloak.getGroupIdByName(tenantCode);
        for (OnboardingIdentityOperation operation : operations.findAllByJob_IdOrderByRow_RowNumberDesc(jobId)) {
            if (operation.getStatus() == OnboardingIdentityOperation.Status.COMPENSATED) continue;
            try {
                if (operation.isCreatedByJob()) {
                    String id = operation.getKeycloakId() == null ? find(value(operation.getRow(), "email")) : operation.getKeycloakId();
                    if (id != null) keycloak.deleteUser(id);
                }
                else {
                    User user = keycloak.getUser(operation.getKeycloakId()); Map<String, List<String>> attributes = user.getAttributes() == null ? new HashMap<>() : new HashMap<>(user.getAttributes());
                    if (operation.getPreviousRoles() == null) attributes.remove(tenantCode + "_roles"); else attributes.put(tenantCode + "_roles", split(operation.getPreviousRoles()));
                    user.setAttributes(attributes); keycloak.updateUser(user); if (!operation.isPreviouslyInGroup()) keycloak.deleteUserGroup(operation.getKeycloakId(), groupId);
                }
                operation.setStatus(OnboardingIdentityOperation.Status.COMPENSATED); operation.setLastErrorCode(null);
            } catch (RuntimeException exception) { success = false; operation.setStatus(OnboardingIdentityOperation.Status.COMPENSATION_FAILED); operation.setLastErrorCode("KEYCLOAK_COMPENSATION_FAILED"); }
            operations.save(operation);
        }
        return success;
    }

    public int sendSetupEmails(Long jobId) {
        int failures = 0;
        for (OnboardingIdentityOperation operation : operations.findAllByJob_IdOrderByRow_RowNumberDesc(jobId)) if (operation.isCreatedByJob() && operation.getStatus() == OnboardingIdentityOperation.Status.APPLIED) {
            try { keycloak.sendExecuteActionsEmail(operation.getKeycloakId(), List.of("UPDATE_PASSWORD", "VERIFY_EMAIL")); } catch (RuntimeException exception) { failures++; }
        }
        return failures;
    }
    private String find(String email) {
        return keycloak.getUsers().stream()
            .filter(user -> email.equalsIgnoreCase(user.getUsername()) || email.equalsIgnoreCase(user.getEmail()))
            .map(User::getId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
    private static String value(OnboardingImportRow row, String key) { return Objects.toString(row.getNormalizedPayload().get(key), ""); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toList()); }
}
