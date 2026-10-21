package com.fundaro.zodiac.taurus.service.loansheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetLine;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturn;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRenewalRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetLineRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Line;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.RegisterEntry;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Signature;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Collects what Taurus recorded about an issued sheet into the content printed by the template: the lines frozen at
 * issue, the register of variations and the final return report.
 */
@Component
public class LoanSheetContentFactory {

    private static final Logger LOG = LoggerFactory.getLogger(LoanSheetContentFactory.class);

    private final InventoryLoanSheetLineRepository lineRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryAssignmentRevisionRepository revisionRepository;
    private final InventoryReturnRepository returnRepository;
    private final InventoryAssignmentRenewalRepository renewalRepository;
    private final LoanSheetTenantData tenantData;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final ObjectMapper objectMapper;

    public LoanSheetContentFactory(
        InventoryLoanSheetLineRepository lineRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryAssignmentRevisionRepository revisionRepository,
        InventoryReturnRepository returnRepository,
        InventoryAssignmentRenewalRepository renewalRepository,
        LoanSheetTenantData tenantData,
        TenantTimeZoneService tenantTimeZoneService,
        ObjectMapper objectMapper
    ) {
        this.lineRepository = lineRepository;
        this.assignmentRepository = assignmentRepository;
        this.revisionRepository = revisionRepository;
        this.returnRepository = returnRepository;
        this.renewalRepository = renewalRepository;
        this.tenantData = tenantData;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.objectMapper = objectMapper;
    }

    /**
     * The whole sheet, as printed on paper.
     */
    public LoanSheetContent content(InventoryLoanSheet sheet, String template, AbstractAuthenticationToken token) {
        return content(sheet, template, null, null, tenantData.forTemplate(template, token));
    }

    /**
     * The content of an act signed electronically; a variation prints only the rows of the register whose origin is in
     * {@code registerKeys}.
     */
    public LoanSheetContent actContent(
        InventoryLoanSheet sheet,
        String template,
        Signature signature,
        Set<String> registerKeys,
        String tenantCode
    ) {
        return content(sheet, template, signature, registerKeys, tenantData.forTemplate(template, tenantCode));
    }

    /**
     * The register of variations and the final return report of a sheet.
     */
    public LoanSheetRegisterBuilder.Result register(InventoryLoanSheet sheet) {
        return register(sheet, lineRepository.findAllBySheet_IdAndDeletedFalseOrderByLineNumberAsc(sheet.getId()), tenantTimeZoneService.currentZoneId());
    }

    public LoanSheetRegisterBuilder.Result register(InventoryLoanSheet sheet, List<InventoryLoanSheetLine> lines, ZoneId zone) {
        List<InventoryAssignment> assignments = assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId());
        List<Long> ids = assignments.stream().map(InventoryAssignment::getId).toList();
        List<InventoryReturn> returns = ids.isEmpty()
            ? List.of()
            : returnRepository.findAllByAssignment_IdInAndStatusAndDeletedFalseOrderByCompletedAtAscIdAsc(ids, InventoryReturnStatus.COMPLETED);
        Map<Long, LocalDate> deliveryExpirations = new HashMap<>();
        for (InventoryAssignment assignment : assignments) {
            if (assignment.getItem().getLoanType() != InventoryLoanType.FIXED_TERM) continue;
            LocalDate atDelivery = initialSnapshot(assignment).expirationDate();
            deliveryExpirations.put(assignment.getId(), atDelivery == null ? assignment.getExpirationDate() : atDelivery);
        }
        return LoanSheetRegisterBuilder.build(
            sheet,
            lines,
            assignments,
            returns,
            renewalRepository.findAllBySheetIdAndDeletedFalseOrderByRenewedOnAscIdAsc(sheet.getId()),
            deliveryExpirations,
            zone
        );
    }

    /**
     * A line of section 3, frozen at issue from the state of the assignment at delivery.
     */
    public InventoryLoanSheetLine snapshotLine(InventoryAssignment assignment, int lineNumber) {
        InventoryItem item = assignment.getItem();
        DeliverySnapshot snapshot = initialSnapshot(assignment);
        InventoryLoanSheetLine line = new InventoryLoanSheetLine();
        line.setLineNumber(lineNumber);
        line.setAssignmentId(assignment.getId());
        line.setItemName(item.getName());
        line.setBrandModel(item.getBrandModel());
        line.setSerialNumber(item.getSerialNumber());
        line.setInventoryNumber(item.getInventoryNumber());
        line.setAccessories(assignment.getAccessories());
        line.setQuantity(assignment.getOutstandingQuantity());
        line.setAgreedUnitValue(LoanSheetText.euroUnitValue(item.getEstimatedUnitValue(), item.getCurrency()));
        line.setConditionStatus(snapshot.conditionStatus() == null ? item.getConditionStatus() : snapshot.conditionStatus());
        line.setConditionNotes(snapshot.conditionStatus() == null ? item.getConditionNotes() : snapshot.conditionNotes());
        line.setLoanType(item.getLoanType());
        line.setExpirationDate(assignment.getExpirationDate());
        return line;
    }

    public static Line toLine(InventoryLoanSheetLine line) {
        return new Line(
            line.getLineNumber(),
            line.getAssignmentId(),
            line.getItemName(),
            line.getBrandModel(),
            line.getSerialNumber(),
            line.getInventoryNumber(),
            line.getAccessories(),
            line.getQuantity(),
            line.getAgreedUnitValue(),
            line.getConditionStatus(),
            line.getConditionNotes(),
            line.getLoanType(),
            line.getExpirationDate()
        );
    }

    private LoanSheetContent content(
        InventoryLoanSheet sheet,
        String template,
        Signature signature,
        Set<String> registerKeys,
        LoanSheetContent.Tenant tenant
    ) {
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        List<InventoryLoanSheetLine> lines = lineRepository.findAllBySheet_IdAndDeletedFalseOrderByLineNumberAsc(sheet.getId());
        LoanSheetRegisterBuilder.Result register = register(sheet, lines, zone);
        List<RegisterEntry> entries = register.register();
        if (registerKeys != null) {
            List<RegisterEntry> selected = new ArrayList<>();
            for (int index = 0; index < entries.size(); index++) {
                if (registerKeys.contains(register.keys().get(index))) selected.add(entries.get(index));
            }
            entries = selected;
        }
        return new LoanSheetContent(
            false,
            tenant,
            sheet.getSheetNumber(),
            InventoryLoanTemplateService.durations(sheet),
            sheet.getHolderRole(),
            sheet.getMemberName(),
            sheet.getMemberLastName(),
            sheet.getMemberBirthDate(),
            sheet.getMemberEmail(),
            lines.stream().map(LoanSheetContentFactory::toLine).toList(),
            sheet.getIssuedOn(),
            sheet.getPlace(),
            sheet.getAuthorizationReference(),
            sheet.getAuthorizationDate(),
            sheet.getAssociationSignatoryName(),
            entries,
            LoanSheetRegisterBuilder.finalReturn(sheet, register),
            signature
        );
    }

    private record DeliverySnapshot(InventoryCondition conditionStatus, String conditionNotes, LocalDate expirationDate) {
    }

    /**
     * State at delivery, read from the first revision acknowledged by the member.
     */
    private DeliverySnapshot initialSnapshot(InventoryAssignment assignment) {
        return revisionRepository.findByAssignment_IdAndRevisionNumber(assignment.getId(), 1).map(revision -> {
            try {
                JsonNode root = objectMapper.readTree(revision.getSnapshotJson());
                String condition = text(root, "conditionStatus");
                String expiration = text(root, "expirationDate");
                return new DeliverySnapshot(
                    condition == null ? null : InventoryCondition.valueOf(condition),
                    text(root, "conditionNotes"),
                    expiration == null ? null : LocalDate.parse(expiration)
                );
            } catch (IOException | IllegalArgumentException | java.time.format.DateTimeParseException exception) {
                LOG.warn("inventory_loan_sheet_snapshot_unreadable assignmentId={}", assignment.getId());
                return new DeliverySnapshot(null, null, null);
            }
        }).orElse(new DeliverySnapshot(null, null, null));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
