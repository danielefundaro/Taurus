package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.projection.InventoryExpirationProjection;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryOperationProvider implements DashboardOperationProvider {

    private static final List<InventoryAssignmentStatus> OUTSTANDING = List.of(
        InventoryAssignmentStatus.ACTIVE,
        InventoryAssignmentStatus.PARTIALLY_RETURNED
    );
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryAssignmentDecisionRepository decisionRepository;
    private final InventoryReturnRepository returnRepository;
    private final ApplicationProperties.DashboardProperties properties;

    public InventoryOperationProvider(
        InventoryAssignmentRepository assignmentRepository,
        InventoryAssignmentDecisionRepository decisionRepository,
        InventoryReturnRepository returnRepository,
        ApplicationProperties applicationProperties
    ) {
        this.assignmentRepository = assignmentRepository;
        this.decisionRepository = decisionRepository;
        this.returnRepository = returnRepository;
        this.properties = applicationProperties.getDashboard();
    }

    @Override
    public DashboardDomain domain() {
        return DashboardDomain.INVENTORY;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
        List<OperationalItemDTO> result = new ArrayList<>();
        boolean administrator = context.hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN);
        long pendingDecisions = administrator
            ? decisionRepository.countPendingCurrentRevisions(OUTSTANDING)
            : decisionRepository.countPendingCurrentRevisionsForUser(context.subject(), OUTSTANDING);
        if (pendingDecisions > 0) {
            DashboardOperationType type = administrator
                ? DashboardOperationType.INVENTORY_DECISIONS_PENDING
                : DashboardOperationType.INVENTORY_DECISION_REQUIRED;
            result.add(item(
                type,
                DashboardSeverity.WARNING,
                pendingDecisions,
                administrator ? "Prese visione da verificare" : "Presa visione richiesta",
                administrator ? "Assegnazioni ancora senza una decisione dell’assegnatario." : "Accetta o rifiuta la revisione corrente delle tue assegnazioni.",
                null,
                administrator ? "Verifica" : "Decidi",
                administrator ? "/inventory?attention=pending-decisions" : "/inventory?view=mine&attention=pending-decisions"
            ));
        }
        if (administrator) {
            long pendingReturns = returnRepository.countByDeletedFalseAndStatus(InventoryReturnStatus.REQUESTED);
            if (pendingReturns > 0) {
                result.add(item(
                    DashboardOperationType.INVENTORY_RETURNS_PENDING,
                    DashboardSeverity.WARNING,
                    pendingReturns,
                    "Riconsegne da verificare",
                    "Richieste di riconsegna in attesa di chiusura amministrativa.",
                    null,
                    "Verifica",
                    "/inventory?attention=pending-returns"
                ));
            }
        }
        addExpiring(context, administrator, result);
        return result;
    }

    private void addExpiring(DashboardRequestContext context, boolean administrator, List<OperationalItemDTO> result) {
        LocalDate today = context.generatedAt().toLocalDate();
        InventoryExpirationProjection summary = assignmentRepository.summarizeExpiring(
            OUTSTANDING,
            today,
            today.plusDays(properties.getInventoryExpirationLookAheadDays()),
            administrator ? null : context.subject()
        );
        if (summary == null || summary.getAssignmentCount() == 0 || summary.getEarliestExpirationDate() == null) return;
        DashboardSeverity severity;
        if (summary.getOverdueCount() > 0) severity = DashboardSeverity.DANGER;
        else if (!summary.getEarliestExpirationDate().isAfter(today.plusDays(properties.getInventoryWarningDays()))) {
            severity = DashboardSeverity.WARNING;
        } else severity = DashboardSeverity.INFO;
        ZonedDateTime dueAt = summary.getEarliestExpirationDate().atStartOfDay(context.zoneId());
        String description = summary.getOverdueCount() > 0
            ? summary.getOverdueCount() + (summary.getOverdueCount() == 1 ? " assegnazione è scaduta." : " assegnazioni sono scadute.")
            : "La prima assegnazione scade il " + summary.getEarliestExpirationDate() + ".";
        result.add(item(
            DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING,
            severity,
            summary.getAssignmentCount(),
            "Assegnazioni in scadenza",
            description,
            dueAt,
            "Apri inventario",
            administrator ? "/inventory?attention=expiring" : "/inventory?view=mine&attention=expiring"
        ));
    }

    private static OperationalItemDTO item(
        DashboardOperationType type,
        DashboardSeverity severity,
        long count,
        String title,
        String description,
        ZonedDateTime dueAt,
        String actionLabel,
        String targetPath
    ) {
        return new OperationalItemDTO(type.name(), type, DashboardDomain.INVENTORY, severity, count, null, title, description, dueAt, actionLabel, targetPath);
    }
}
