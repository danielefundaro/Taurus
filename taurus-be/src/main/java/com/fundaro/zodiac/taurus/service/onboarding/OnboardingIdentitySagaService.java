package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.Group;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class OnboardingIdentitySagaService {
    private final KeycloakService keycloak;
    private final OnboardingIdentityOperationRepository operations;
    private final OnboardingMetrics metrics;
    private final int concurrency;

    public OnboardingIdentitySagaService(KeycloakService keycloak, OnboardingIdentityOperationRepository operations,
                                         OnboardingMetrics metrics, ApplicationProperties applicationProperties) {
        this.keycloak = keycloak;
        this.operations = operations;
        this.metrics = metrics;
        this.concurrency = applicationProperties.getOnboarding().getIdentityConcurrency();
    }

    /**
     * Prepares the identities of the rows. The addresses already known to Keycloak are read once, not once per row,
     * and the rows are prepared a few at a time ({@code identity-concurrency}, four by default), because every row
     * costs several calls to Keycloak. Each row still writes its journal entry before its own external call and marks
     * it {@code APPLIED} after it, so the compensation finds every identity touched, whatever order they ran in.
     */
    public Map<Long, String> prepare(OnboardingImportJob job, List<OnboardingImportRow> userRows, String tenantCode) {
        Map<Long, String> identities = Collections.synchronizedMap(new HashMap<>());
        String groupId = keycloak.getGroupIdByName(tenantCode);
        KnownIdentities known = new KnownIdentities();
        List<OnboardingImportRow> pending = userRows.stream().filter(row -> row.getAction() != OnboardingRowAction.SKIP).toList();
        int threads = Math.min(concurrency, Math.max(1, pending.size()));
        if (threads == 1) {
            for (OnboardingImportRow row : pending) prepareRow(job, row, tenantCode, groupId, known, identities);
            return identities;
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> started = pending.stream()
                .map(row -> pool.<Void>submit(() -> {
                    TenantContext.run(tenantCode, () -> prepareRow(job, row, tenantCode, groupId, known, identities));
                    return null;
                }))
                .toList();
            RuntimeException failure = null;
            // Every row is waited for even after the first failure: the compensation reads the journal, and a row
            // still calling Keycloak would leave an identity out of it.
            for (Future<Void> future : started) {
                try {
                    future.get();
                } catch (ExecutionException exception) {
                    if (failure == null) failure = failure(exception.getCause());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Identity preparation interrupted", exception);
                }
            }
            if (failure != null) throw failure;
            return identities;
        } finally {
            pool.shutdown();
        }
    }

    private void prepareRow(OnboardingImportJob job, OnboardingImportRow row, String tenantCode, String groupId,
                            KnownIdentities known, Map<Long, String> identities) {
        Optional<OnboardingIdentityOperation> previousOperation = operations.findByRow_Id(row.getId());
        if (previousOperation.filter(operation -> operation.getStatus() == OnboardingIdentityOperation.Status.APPLIED).isPresent()) {
            identities.put(row.getId(), previousOperation.orElseThrow().getKeycloakId());
            return;
        }
        String email = value(row, "email");
        String id = known.id(email);
        boolean created = previousOperation.map(OnboardingIdentityOperation::isCreatedByJob).orElse(id == null);
        OnboardingIdentityOperation operation = previousOperation.orElseGet(OnboardingIdentityOperation::new);
        operation.setJob(job);
        operation.setRow(row);
        operation.setOperationType(created ? OnboardingIdentityOperation.Type.CREATE : OnboardingIdentityOperation.Type.LINK_EXISTING);
        operation.setCreatedByJob(created);
        if (previousOperation.isEmpty())
            operation.setSetupEmailStatus(created ? OnboardingIdentityOperation.SetupEmailStatus.PENDING : OnboardingIdentityOperation.SetupEmailStatus.NOT_REQUESTED);
        if (created && id == null) {
            operations.save(operation);
            User user = new User();
            user.setUsername(email);
            user.setEmail(email);
            user.setFirstName(value(row, "nome"));
            user.setLastName(value(row, "cognome"));
            user.setEnabled("SI".equals(value(row, "attivo")));
            user.setEmailVerified(false);
            keycloak.saveUser(user);
            id = keycloak.getUserIdByUsernameOrEmail(email, email);
        }
        User current = keycloak.getUser(id);
        List<Group> groups = keycloak.getUserGroups(id);
        boolean alreadyInGroup = groups.stream().anyMatch(group -> tenantCode.equals(group.getName()));
        operation.setPreviouslyInGroup(alreadyInGroup);
        Map<String, List<String>> attributes = current.getAttributes() == null ? new HashMap<>() : new HashMap<>(current.getAttributes());
        List<String> previous = attributes.get(tenantCode + "_roles");
        operation.setPreviousRoles(previous == null ? null : String.join("|", previous));
        operation.setKeycloakId(id);
        operations.save(operation);
        try {
            if (!alreadyInGroup) keycloak.updateUserGroup(id, groupId);
            attributes.put(tenantCode + "_roles", split(value(row, "ruoli")).stream().map(role -> "ROLE_" + role).toList());
            current.setAttributes(attributes);
            keycloak.updateUser(current);
            operation.setStatus(OnboardingIdentityOperation.Status.APPLIED);
            operations.save(operation);
            identities.put(row.getId(), id);
            metrics.identity(created ? "create" : "link", true);
        } catch (RuntimeException exception) {
            operation.setLastErrorCode("KEYCLOAK_PREPARE_FAILED");
            operations.save(operation);
            metrics.identity(created ? "create" : "link", false);
            throw exception;
        }
    }

    private static RuntimeException failure(Throwable cause) {
        return cause instanceof RuntimeException runtime ? runtime : new IllegalStateException("Identity preparation failed", cause);
    }

    public boolean compensate(Long jobId, String tenantCode) {
        boolean success = true;
        String groupId = keycloak.getGroupIdByName(tenantCode);
        for (OnboardingIdentityOperation operation : operations.findAllByJob_IdOrderByRow_RowNumberDesc(jobId)) {
            if (operation.getStatus() == OnboardingIdentityOperation.Status.COMPENSATED) continue;
            try {
                if (operation.isCreatedByJob()) {
                    String id = operation.getKeycloakId() == null ? find(value(operation.getRow(), "email")) : operation.getKeycloakId();
                    if (id != null) {
                        boolean linkedElsewhere = keycloak.getUserGroups(id).stream().anyMatch(group -> !tenantCode.equals(group.getName()));
                        if (linkedElsewhere) {
                            User user = keycloak.getUser(id);
                            user.setEnabled(false);
                            keycloak.updateUser(user);
                            throw new IllegalStateException("Created identity is linked to another group");
                        }
                        keycloak.deleteUser(id);
                    }
                } else {
                    User user = keycloak.getUser(operation.getKeycloakId());
                    Map<String, List<String>> attributes = user.getAttributes() == null ? new HashMap<>() : new HashMap<>(user.getAttributes());
                    if (operation.getPreviousRoles() == null) attributes.remove(tenantCode + "_roles");
                    else attributes.put(tenantCode + "_roles", split(operation.getPreviousRoles()));
                    user.setAttributes(attributes);
                    keycloak.updateUser(user);
                    if (!operation.isPreviouslyInGroup()) keycloak.deleteUserGroup(operation.getKeycloakId(), groupId);
                }
                operation.setStatus(OnboardingIdentityOperation.Status.COMPENSATED);
                operation.setLastErrorCode(null);
                metrics.identity("compensate", true);
            } catch (RuntimeException exception) {
                success = false;
                operation.setStatus(OnboardingIdentityOperation.Status.COMPENSATION_FAILED);
                operation.setLastErrorCode("KEYCLOAK_COMPENSATION_FAILED");
                metrics.identity("compensate", false);
            }
            operations.save(operation);
        }
        return success;
    }

    public int sendSetupEmails(Long jobId) {
        return deliverSetupEmails(jobId, false);
    }

    public int retryFailedSetupEmails(Long jobId) {
        return deliverSetupEmails(jobId, true);
    }

    private int deliverSetupEmails(Long jobId, boolean failedOnly) {
        List<OnboardingIdentityOperation> journal = operations.findAllByJob_IdOrderByRow_RowNumberDesc(jobId);
        for (OnboardingIdentityOperation operation : journal) {
            if (!operation.isCreatedByJob() || operation.getStatus() != OnboardingIdentityOperation.Status.APPLIED)
                continue;
            if (failedOnly && operation.getSetupEmailStatus() != OnboardingIdentityOperation.SetupEmailStatus.FAILED)
                continue;
            if (!failedOnly && operation.getSetupEmailStatus() != OnboardingIdentityOperation.SetupEmailStatus.PENDING && operation.getSetupEmailStatus() != OnboardingIdentityOperation.SetupEmailStatus.FAILED)
                continue;
            try {
                keycloak.sendExecuteActionsEmail(operation.getKeycloakId(), List.of("UPDATE_PASSWORD", "VERIFY_EMAIL"));
                operation.setSetupEmailStatus(OnboardingIdentityOperation.SetupEmailStatus.SENT);
                operation.setLastErrorCode(null);
                metrics.setupEmail(true);
            } catch (RuntimeException exception) {
                operation.setSetupEmailStatus(OnboardingIdentityOperation.SetupEmailStatus.FAILED);
                operation.setLastErrorCode("SETUP_EMAIL_FAILED");
                metrics.setupEmail(false);
            }
            operations.save(operation);
        }
        return Math.toIntExact(journal.stream().filter(operation -> operation.getSetupEmailStatus() == OnboardingIdentityOperation.SetupEmailStatus.FAILED).count());
    }

    /**
     * The identities Keycloak knows, read at most once for the whole import and only when a row needs them: a job
     * resumed on rows already applied asks Keycloak nothing.
     */
    private final class KnownIdentities {
        private Map<String, String> value;

        private synchronized String id(String email) {
            if (value == null) value = knownIdentities();
            return value.get(email.trim().toLowerCase(Locale.ROOT));
        }
    }

    /** Every address Keycloak knows, by username and by e-mail. */
    private Map<String, String> knownIdentities() {
        Map<String, String> result = new HashMap<>();
        for (User user : keycloak.getUsers()) {
            if (user.getId() == null) continue;
            if (user.getUsername() != null) result.putIfAbsent(user.getUsername().trim().toLowerCase(Locale.ROOT), user.getId());
            if (user.getEmail() != null) result.putIfAbsent(user.getEmail().trim().toLowerCase(Locale.ROOT), user.getId());
        }
        return result;
    }

    private String find(String email) {
        return knownIdentities().get(email.trim().toLowerCase(Locale.ROOT));
    }

    private static String value(OnboardingImportRow row, String key) {
        return Objects.toString(row.getNormalizedPayload().get(key), "");
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split("\\|")).map(String::trim).filter(v -> !v.isBlank()).collect(Collectors.toList());
    }
}
