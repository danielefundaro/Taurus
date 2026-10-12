package com.fundaro.zodiac.taurus.service.loansheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.inventory.*;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.*;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Durations;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.web.rest.errors.ErrorArgs;

import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.fundaro.zodiac.taurus.service.loansheet.LoanSheetSupport.*;

/**
 * Life cycle of the loan sheet: issue, later deliveries, renewals, substitutions, closure, settlement and PDF.
 */
@Service
@Transactional
public class InventoryLoanSheetService {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryLoanSheetService.class);
    static final String MEDIA_CATEGORY = "inventory-loan-sheets";
    private static final List<InventoryAssignmentStatus> OUTSTANDING = List.of(
        InventoryAssignmentStatus.ACTIVE,
        InventoryAssignmentStatus.PARTIALLY_RETURNED
    );
    private static final List<InventoryLoanSheetStatus> VISIBLE_TO_MEMBER = List.of(
        InventoryLoanSheetStatus.ISSUED,
        InventoryLoanSheetStatus.CLOSED,
        InventoryLoanSheetStatus.SETTLED
    );

    private final InventoryLoanSheetRepository sheetRepository;
    private final InventoryLoanSheetLineRepository lineRepository;
    private final InventoryLoanSheetCounterRepository counterRepository;
    private final InventoryAssignmentRenewalRepository renewalRepository;
    private final InventoryLoanSheetExportRepository exportRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryAssignmentRevisionRepository revisionRepository;
    private final InventoryReturnRepository returnRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryLoanTemplateService templateService;
    private final InventoryLoanSheetPolicy policy;
    private final InventoryService inventoryService;
    private final UsersService usersService;
    private final LoanSheetTenantData tenantData;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;
    private final LoanSheetPdfRenderer renderer;
    private final ObjectMapper objectMapper;

    public InventoryLoanSheetService(
        InventoryLoanSheetRepository sheetRepository,
        InventoryLoanSheetLineRepository lineRepository,
        InventoryLoanSheetCounterRepository counterRepository,
        InventoryAssignmentRenewalRepository renewalRepository,
        InventoryLoanSheetExportRepository exportRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryAssignmentRevisionRepository revisionRepository,
        InventoryReturnRepository returnRepository,
        InventoryItemRepository itemRepository,
        InventoryLoanTemplateService templateService,
        InventoryLoanSheetPolicy policy,
        InventoryService inventoryService,
        UsersService usersService,
        LoanSheetTenantData tenantData,
        TenantTimeZoneService tenantTimeZoneService,
        MediaService mediaService,
        MediaRepository mediaRepository,
        LoanSheetPdfRenderer renderer,
        ObjectMapper objectMapper
    ) {
        this.sheetRepository = sheetRepository;
        this.lineRepository = lineRepository;
        this.counterRepository = counterRepository;
        this.renewalRepository = renewalRepository;
        this.exportRepository = exportRepository;
        this.assignmentRepository = assignmentRepository;
        this.revisionRepository = revisionRepository;
        this.returnRepository = returnRepository;
        this.itemRepository = itemRepository;
        this.templateService = templateService;
        this.policy = policy;
        this.inventoryService = inventoryService;
        this.usersService = usersService;
        this.tenantData = tenantData;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
        this.renderer = renderer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Summary> findForUser(Long userIndex, AbstractAuthenticationToken token) {
        tenant(token);
        return sheetRepository.findAllByUserIndexAndDeletedFalseOrderByIssuedOnDescIdDesc(userIndex).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<Summary> findOwn(AbstractAuthenticationToken token) {
        tenant(token);
        return sheetRepository.findAllByUserKeycloakIdAndDeletedFalseAndStatusInOrderByIssuedOnDescIdDesc(actor(token), VISIBLE_TO_MEMBER)
            .stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, AbstractAuthenticationToken token) {
        tenant(token);
        return toDetail(requiredSheet(id));
    }

    public ExpirationRange expirationRange(Long userIndex, LocalDate deliveredOn, AbstractAuthenticationToken token) {
        tenant(token);
        return policy.expirationRange(userIndex, deliveredOn, actor(token));
    }

    public Proposal proposal(Long userIndex, AbstractAuthenticationToken token) {
        tenant(token);
        UsersDTO user = requiredUser(userIndex, token);
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        LocalDate today = LocalDate.now(zone);
        LocalDate birthDate = localDate(user.getBirthDate(), zone);
        InventoryLoanSettings settings = templateService.settings(actor(token));
        Durations parameters = InventoryLoanTemplateService.durations(settings);
        Optional<InventoryLoanSheet> openSheet = sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(userIndex, InventoryLoanSheetStatus.ISSUED);
        List<ProposalAssignment> assignments = assignmentRepository
            .findAllByUserIndexAndLoanSheetIdIsNullAndDeletedFalseAndStatusInOrderByAssignedAtAscIdAsc(userIndex, OUTSTANDING).stream()
            .map(assignment -> new ProposalAssignment(
                assignment.getId(),
                assignment.getItem().getId(),
                assignment.getItem().getName(),
                assignment.getItem().getInventoryNumber(),
                assignment.getItem().getLoanType(),
                assignment.getOutstandingQuantity(),
                localDate(assignment.getAssignedAt(), zone),
                assignment.getExpirationDate(),
                deliveryIssues(assignment, parameters, zone)
            ))
            .toList();
        return new Proposal(
            userIndex,
            user.getName(),
            user.getLastName(),
            birthDate,
            suggestedRole(birthDate, today),
            birthDate == null,
            today,
            tenantData.dto(token).map(TenantsDTO::getCity).orElse(null),
            sheetRepository.findFirstByAssociationSignatoryNameIsNotNullAndDeletedFalseOrderByIdDesc()
                .map(InventoryLoanSheet::getAssociationSignatoryName).orElse(null),
            openSheet.map(InventoryLoanSheet::getId).orElse(null),
            openSheet.map(InventoryLoanSheet::getSheetNumber).orElse(null),
            new Terms(null, templateService.currentLabel(settings), parameters.minMonths(), parameters.maxMonths(), parameters.renewalMaxMonths(), parameters.totalMaxMonths()),
            assignments
        );
    }

    public PdfContent previewForUser(Long userIndex, IssueRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        UsersDTO user = requiredUser(userIndex, token);
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        InventoryLoanSettings settings = templateService.settings(actor(token));
        String template = templateService.currentContent(settings);
        List<InventoryAssignment> assignments = requestedAssignments(userIndex, request.assignmentIds(), false);
        LocalDate birthDate = localDate(user.getBirthDate(), zone);
        InventoryLoanHolderRole role = request.holderRole() != null ? request.holderRole() : suggestedRole(birthDate, request.issuedOn());
        List<Line> lines = new ArrayList<>();
        for (InventoryAssignment assignment : assignments) lines.add(toLine(snapshotLine(assignment, lines.size() + 1)));
        LoanSheetContent content = new LoanSheetContent(
            true,
            tenantData.forTemplate(template, token),
            null,
            InventoryLoanTemplateService.durations(settings),
            role,
            user.getName(),
            user.getLastName(),
            birthDate,
            user.getEmail(),
            lines,
            request.issuedOn(),
            trimToNull(request.place()),
            trimToNull(request.authorizationReference()),
            request.authorizationDate(),
            trimToNull(request.associationSignatoryName()),
            List.of(),
            null
        );
        return new PdfContent("anteprima-foglio-affidamento.pdf", renderer.render(template, LoanSheetModelFactory.build(content), true));
    }

    public Detail issue(Long userIndex, IssueRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        UsersDTO user = requiredUser(userIndex, token);
        sheetRepository.lockTenantSheets();
        sheetRepository.findFirstByUserIndexAndStatusAndDeletedFalse(userIndex, InventoryLoanSheetStatus.ISSUED).ifPresent(sheet -> {
            throw error(HttpStatus.CONFLICT, "Il socio ha già il foglio di affidamento n. " + sheet.getSheetNumber() + " emesso", "inventory.loanSheet.alreadyIssued").with("sheetNumber", sheet.getSheetNumber());
        });
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        LocalDate birthDate = localDate(user.getBirthDate(), zone);
        InventoryLoanHolderRole role = request.holderRole() != null ? request.holderRole() : suggestedRole(birthDate, request.issuedOn());
        if (role == null) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Indicare se l'Affidatario è il socio maggiorenne o il genitore o tutore", "inventory.loanSheet.roleRequired");
        }
        List<InventoryAssignment> assignments = requestedAssignments(userIndex, request.assignmentIds(), true);
        InventoryLoanSettings settings = templateService.settings(actor);
        Durations parameters = InventoryLoanTemplateService.durations(settings);
        List<ErrorArgs> problems = new ArrayList<>();
        for (InventoryAssignment assignment : assignments) {
            deliveryIssues(assignment, parameters, zone).forEach(issue -> problems.add(
                new ErrorArgs()
                    .with("assignmentId", assignment.getId())
                    .with("itemName", assignment.getItem().getName())
                    .with("inventoryNumber", assignment.getItem().getInventoryNumber())
                    .with("code", issue.code())
                    .with("args", issue.args())
            ));
        }
        if (!problems.isEmpty()) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Loan sheet issue blocked by duration rules", "inventory.loanSheet.validation")
                .with("issues", problems);
        }

        int year = request.issuedOn().getYear();
        InventoryLoanSheetCounter counter = counterRepository.findForUpdate(year).orElseGet(() -> {
            InventoryLoanSheetCounter created = new InventoryLoanSheetCounter();
            created.initializeAudit(actor);
            created.setSheetYear(year);
            created.setLastSequence(0);
            return counterRepository.saveAndFlush(created);
        });
        int sequence = counter.getLastSequence() + 1;
        counter.setLastSequence(sequence);
        counter.touchAudit(actor);
        counterRepository.save(counter);

        InventoryLoanSheet sheet = new InventoryLoanSheet();
        sheet.initializeAudit(actor);
        sheet.setSheetYear(year);
        sheet.setSheetSequence(sequence);
        sheet.setSheetNumber(String.format(Locale.ROOT, "%d/%03d", year, sequence));
        sheet.setStatus(InventoryLoanSheetStatus.ISSUED);
        sheet.setUserIndex(userIndex);
        sheet.setUserKeycloakId(assignments.get(0).getUserKeycloakId());
        sheet.setMemberName(Objects.requireNonNullElse(user.getName(), ""));
        sheet.setMemberLastName(Objects.requireNonNullElse(user.getLastName(), ""));
        sheet.setMemberBirthDate(birthDate);
        sheet.setMemberEmail(trimToNull(user.getEmail()));
        sheet.setHolderRole(role);
        sheet.setIssuedOn(request.issuedOn());
        sheet.setPlace(trimToNull(request.place()));
        sheet.setAuthorizationReference(trimToNull(request.authorizationReference()));
        sheet.setAuthorizationDate(request.authorizationDate());
        sheet.setAssociationSignatoryName(trimToNull(request.associationSignatoryName()));
        InventoryLoanTemplate template = templateService.templateForIssue(settings, actor);
        sheet.setTemplate(template);
        sheet.setTemplateHash(template.getContentHash());
        sheet.setMinMonths(parameters.minMonths());
        sheet.setMaxMonths(parameters.maxMonths());
        sheet.setRenewalMaxMonths(parameters.renewalMaxMonths());
        sheet.setTotalMaxMonths(parameters.totalMaxMonths());
        sheetRepository.save(sheet);

        int lineNumber = 0;
        for (InventoryAssignment assignment : assignments) {
            InventoryLoanSheetLine line = snapshotLine(assignment, ++lineNumber);
            line.initializeAudit(actor);
            line.setSheet(sheet);
            lineRepository.save(line);
            assignment.setLoanSheetId(sheet.getId());
            assignment.touchAudit(actor);
            assignmentRepository.save(assignment);
        }
        LOG.info("inventory_loan_sheet_issued sheetId={} number={} lines={} templateId={}", sheet.getId(), sheet.getSheetNumber(), assignments.size(), template.getId());
        return toDetail(sheet);
    }

    public Detail linkAssignment(long sheetId, long assignmentId, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanSheet sheet = requiredIssuedSheetForUpdate(sheetId);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId).orElseThrow(() -> notFound("Assegnazione non trovata", "inventory.assignment.notFound"));
        if (!sheet.getUserIndex().equals(assignment.getUserIndex())) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "L'assegnazione appartiene a un altro socio", "inventory.loanSheet.otherMember");
        }
        if (assignment.getLoanSheetId() != null) {
            throw error(HttpStatus.CONFLICT, "L'assegnazione è già collegata a un foglio di affidamento", "inventory.loanSheet.alreadyLinked").with("itemName", assignment.getItem().getName());
        }
        if (assignment.getOutstandingQuantity() < 1) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "L'assegnazione non ha materiale in carico", "inventory.loanSheet.nothingOutstanding").with("itemName", assignment.getItem().getName());
        }
        policy.checkDelivery(sheet, assignment.getItem().getLoanType(), localDate(assignment.getAssignedAt(), tenantTimeZoneService.currentZoneId()), assignment.getExpirationDate());
        assignment.setLoanSheetId(sheet.getId());
        assignment.touchAudit(actor);
        assignmentRepository.save(assignment);
        return toDetail(sheet);
    }

    public InventoryAssignmentDTO renew(long assignmentId, RenewalRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId).orElseThrow(() -> notFound("Assegnazione non trovata", "inventory.assignment.notFound"));
        InventoryLoanSheet sheet = issuedSheetOf(assignment, "Il rinnovo è disponibile per i beni di un foglio di affidamento emesso");
        if (assignment.getItem().getLoanType() != InventoryLoanType.FIXED_TERM) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Si rinnova soltanto un comodato con scadenza", "inventory.renewal.notFixedTerm");
        }
        if (assignment.getOutstandingQuantity() < 1) {
            throw error(HttpStatus.CONFLICT, "Il bene è già stato riconsegnato", "inventory.renewal.nothingOutstanding");
        }
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        LocalDate renewedOn = request.renewedOn() == null ? LocalDate.now(zone) : request.renewedOn();
        LocalDate previousExpiration = assignment.getExpirationDate();
        LoanDurationPolicy.checkRenewal(
            previousExpiration,
            request.newExpirationDate(),
            renewedOn,
            request.authorizationDate(),
            firstDelivery(sheet, assignment, zone),
            InventoryLoanTemplateService.durations(sheet)
        ).ifPresent(violation -> {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Renewal breaks the duration rules", "inventory.renewal." + violation.code())
                .with(violation.args());
        });
        InventoryAssignmentDTO renewed = inventoryService.applyRenewal(assignmentId, request.newExpirationDate(), token);
        InventoryAssignmentRenewal renewal = new InventoryAssignmentRenewal();
        renewal.initializeAudit(actor);
        renewal.setAssignment(assignment);
        renewal.setSheetId(sheet.getId());
        renewal.setRenewedOn(renewedOn);
        renewal.setPreviousExpirationDate(previousExpiration);
        renewal.setNewExpirationDate(request.newExpirationDate());
        renewal.setAuthorizationReference(request.authorizationReference().trim());
        renewal.setAuthorizationDate(request.authorizationDate());
        renewalRepository.save(renewal);
        LOG.info("inventory_loan_sheet_renewal sheetId={} assignmentId={} newExpiration={}", sheet.getId(), assignmentId, request.newExpirationDate());
        return renewed;
    }

    public InventoryAssignmentDTO substitute(long assignmentId, SubstitutionRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryAssignment assignment = assignmentRepository.findForUpdate(assignmentId).orElseThrow(() -> notFound("Assegnazione non trovata", "inventory.assignment.notFound"));
        InventoryLoanSheet sheet = issuedSheetOf(assignment, "La sostituzione è disponibile per i beni di un foglio di affidamento emesso");
        InventoryItem item = itemRepository.findByIdAndDeletedFalse(request.itemId()).orElseThrow(() -> notFound("Oggetto inventario non trovato", "inventory.item.notFound"));
        policy.checkDelivery(sheet, item.getLoanType(), LocalDate.now(tenantTimeZoneService.currentZoneId()), request.expirationDate());
        InventoryAssignmentDTO delivered = inventoryService.substitute(assignmentId, request, token);
        LOG.info("inventory_loan_sheet_substitution sheetId={} replacedAssignmentId={} assignmentId={}", sheet.getId(), assignmentId, delivered.id());
        return delivered;
    }

    public Detail close(long id, CloseRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        InventoryLoanSheet sheet = requiredIssuedSheetForUpdate(id);
        if (!closable(sheet)) {
            throw error(HttpStatus.CONFLICT, "Il foglio si chiude quando tutti i beni sono stati riconsegnati", "inventory.loanSheet.notClosable");
        }
        boolean charges = request.outcome() == InventoryLoanReturnOutcome.CHARGES;
        if (charges && (request.chargeAmount() == null || request.chargeAmount().signum() <= 0 || request.chargeDueDate() == null)) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Con addebito occorre indicare importo e termine di pagamento", "inventory.loanSheet.chargesRequired");
        }
        String receivedBy = trimToNull(request.receivedByName());
        if (receivedBy == null) {
            receivedBy = usersService.findMe(token)
                .map(user -> LoanSheetText.join(" ", user.getName(), user.getLastName()))
                .orElse(null);
        }
        sheet.setStatus(InventoryLoanSheetStatus.CLOSED);
        sheet.setClosedAt(ZonedDateTime.now());
        sheet.setClosedBy(actor);
        sheet.setReturnOutcome(request.outcome());
        sheet.setChargeAmount(charges ? request.chargeAmount() : null);
        sheet.setChargeDueDate(charges ? request.chargeDueDate() : null);
        sheet.setReceivedByName(receivedBy);
        sheet.touchAudit(actor);
        sheetRepository.save(sheet);
        LOG.info("inventory_loan_sheet_closed sheetId={} outcome={}", sheet.getId(), request.outcome());
        return toDetail(sheet);
    }

    public Detail settle(long id, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryLoanSheet sheet = sheetRepository.findForUpdate(id).orElseThrow(() -> notFound("Foglio di affidamento non trovato", "inventory.loanSheet.notFound"));
        if (sheet.getStatus() != InventoryLoanSheetStatus.CLOSED) {
            throw error(HttpStatus.CONFLICT, "Si definisce soltanto un foglio chiuso", "inventory.loanSheet.notClosed");
        }
        settleSheet(sheet, tenant, actor);
        return toDetail(sheet);
    }

    public Detail cancel(long id, CancelRequest request, AbstractAuthenticationToken token) {
        String tenant = tenant(token);
        String actor = actor(token);
        InventoryLoanSheet sheet = requiredIssuedSheetForUpdate(id);
        for (InventoryAssignment assignment : assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId())) {
            assignment.setLoanSheetId(null);
            assignment.touchAudit(actor);
            assignmentRepository.save(assignment);
        }
        sheet.setStatus(InventoryLoanSheetStatus.CANCELLED);
        sheet.setCancelledAt(ZonedDateTime.now());
        sheet.setCancelledBy(actor);
        sheet.setCancellationReason(request.reason().trim());
        sheet.touchAudit(actor);
        sheetRepository.save(sheet);
        deletePdfs(sheet, tenant, actor);
        LOG.info("inventory_loan_sheet_cancelled sheetId={}", sheet.getId());
        return toDetail(sheet);
    }

    public PdfContent pdf(long id, AbstractAuthenticationToken token) {
        tenant(token);
        return generate(requiredSheet(id), token);
    }

    public PdfContent ownPdf(long id, AbstractAuthenticationToken token) {
        tenant(token);
        InventoryLoanSheet sheet = sheetRepository.findByIdAndUserKeycloakIdAndDeletedFalse(id, actor(token))
            .orElseThrow(() -> notFound("Foglio di affidamento non trovato", "inventory.loanSheet.notFound"));
        return generate(sheet, token);
    }

    /**
     * Settles, in the current tenant, the sheets closed without charges before the given instant.
     */
    public int settleClosedWithoutCharges(ZonedDateTime closedBefore, String tenantCode, String actor) {
        List<InventoryLoanSheet> sheets = sheetRepository.findAllByStatusAndReturnOutcomeAndClosedAtBeforeAndDeletedFalse(
            InventoryLoanSheetStatus.CLOSED,
            InventoryLoanReturnOutcome.COMPLIANT,
            closedBefore
        );
        for (InventoryLoanSheet candidate : sheets) {
            sheetRepository.findForUpdate(candidate.getId())
                .filter(sheet -> sheet.getStatus() == InventoryLoanSheetStatus.CLOSED)
                .ifPresent(sheet -> settleSheet(sheet, tenantCode, actor));
        }
        return sheets.size();
    }

    /**
     * Removes the personal data of every sheet of a member erased under GDPR.
     */
    public void purgeForUser(String userKeycloakId, String pseudonym, String tenantCode, String actor) {
        for (InventoryLoanSheet sheet : sheetRepository.findAllByUserKeycloakIdAndDeletedFalse(userKeycloakId)) {
            purgePersonalData(sheet, actor);
            sheet.setUserKeycloakId(pseudonym);
            sheetRepository.save(sheet);
            deletePdfs(sheet, tenantCode, actor);
        }
    }

    private PdfContent generate(InventoryLoanSheet sheet, AbstractAuthenticationToken token) {
        if (sheet.getStatus() == InventoryLoanSheetStatus.CANCELLED) {
            throw error(HttpStatus.CONFLICT, "Il foglio di affidamento è stato annullato", "inventory.loanSheet.cancelled");
        }
        if (sheet.getPersonalDataPurgedAt() != null) {
            throw error(HttpStatus.GONE, "I dati personali del foglio sono stati rimossi: il PDF non è più disponibile", "inventory.loanSheet.purged");
        }
        String actor = actor(token);
        String template = sheet.getTemplate().getContent();
        byte[] bytes = renderer.render(template, LoanSheetModelFactory.build(content(sheet, template, token)), false);
        if (bytes.length > LoanSheetPdfRenderer.MAX_PDF_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Il foglio di affidamento generato supera 20 MB", "inventory.loanSheet.tooLarge").with("maxMegabytes", (int) (LoanSheetPdfRenderer.MAX_PDF_BYTES / (1024 * 1024)));
        }
        String fileName = "foglio-affidamento-" + sheet.getSheetNumber().replace('/', '-') + ".pdf";
        MediaDTO media = mediaService.store(bytes, fileName, "application/pdf", MEDIA_CATEGORY, token);
        InventoryLoanSheetExport export = new InventoryLoanSheetExport();
        export.initializeAudit(actor);
        export.setSheet(sheet);
        export.setMediaAsset(mediaRepository.getReferenceById(media.getId()));
        export.setGeneratedBy(actor);
        export.setGeneratedAt(ZonedDateTime.now());
        exportRepository.save(export);
        LOG.info("inventory_loan_sheet_pdf sheetId={} bytes={}", sheet.getId(), bytes.length);
        return new PdfContent(fileName, bytes);
    }

    private LoanSheetContent content(InventoryLoanSheet sheet, String template, AbstractAuthenticationToken token) {
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        List<InventoryLoanSheetLine> lines = lineRepository.findAllBySheet_IdAndDeletedFalseOrderByLineNumberAsc(sheet.getId());
        LoanSheetRegisterBuilder.Result register = register(sheet, lines, zone);
        return new LoanSheetContent(
            false,
            tenantData.forTemplate(template, token),
            sheet.getSheetNumber(),
            InventoryLoanTemplateService.durations(sheet),
            sheet.getHolderRole(),
            sheet.getMemberName(),
            sheet.getMemberLastName(),
            sheet.getMemberBirthDate(),
            sheet.getMemberEmail(),
            lines.stream().map(InventoryLoanSheetService::toLine).toList(),
            sheet.getIssuedOn(),
            sheet.getPlace(),
            sheet.getAuthorizationReference(),
            sheet.getAuthorizationDate(),
            sheet.getAssociationSignatoryName(),
            register.register(),
            LoanSheetRegisterBuilder.finalReturn(sheet, register)
        );
    }

    private LoanSheetRegisterBuilder.Result register(InventoryLoanSheet sheet, List<InventoryLoanSheetLine> lines, ZoneId zone) {
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

    private void settleSheet(InventoryLoanSheet sheet, String tenantCode, String actor) {
        sheet.setStatus(InventoryLoanSheetStatus.SETTLED);
        sheet.setSettledAt(ZonedDateTime.now());
        sheet.setSettledBy(actor);
        purgePersonalData(sheet, actor);
        sheetRepository.save(sheet);
        deletePdfs(sheet, tenantCode, actor);
        LOG.info("inventory_loan_sheet_settled sheetId={}", sheet.getId());
    }

    private static void purgePersonalData(InventoryLoanSheet sheet, String actor) {
        sheet.setMemberName("Dati personali");
        sheet.setMemberLastName("rimossi");
        sheet.setMemberBirthDate(null);
        sheet.setMemberEmail(null);
        sheet.setPersonalDataPurgedAt(ZonedDateTime.now());
        sheet.touchAudit(actor);
    }

    private void deletePdfs(InventoryLoanSheet sheet, String tenantCode, String actor) {
        for (InventoryLoanSheetExport export : exportRepository.findAllBySheet_IdAndMediaAssetIsNotNull(sheet.getId())) {
            Long mediaId = export.getMediaAsset().getId();
            export.setMediaAsset(null);
            export.touchAudit(actor);
            exportRepository.saveAndFlush(export);
            mediaService.deleteIfUnreferenced(mediaId, tenantCode, actor);
        }
    }

    private boolean closable(InventoryLoanSheet sheet) {
        if (sheet.getStatus() != InventoryLoanSheetStatus.ISSUED) return false;
        List<InventoryAssignment> assignments = assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId());
        return !assignments.isEmpty() && assignments.stream().allMatch(assignment -> assignment.getOutstandingQuantity() == 0);
    }

    private List<InventoryAssignment> requestedAssignments(Long userIndex, List<Long> requestedIds, boolean lock) {
        List<Long> ids = requestedIds.stream().filter(Objects::nonNull).distinct().toList();
        List<InventoryAssignment> assignments = lock
            ? assignmentRepository.findAllForUpdate(ids)
            : assignmentRepository.findAllById(ids).stream().filter(assignment -> !assignment.isDeleted())
                .sorted(Comparator.comparing(InventoryAssignment::getAssignedAt).thenComparing(InventoryAssignment::getId)).toList();
        if (assignments.size() != ids.size()) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Una o più assegnazioni non sono disponibili", "inventory.loanSheet.assignmentNotFound");
        }
        for (InventoryAssignment assignment : assignments) {
            if (!userIndex.equals(assignment.getUserIndex())) {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Le assegnazioni devono appartenere al socio del foglio", "inventory.loanSheet.otherMember");
            }
            if (assignment.getLoanSheetId() != null) {
                throw error(HttpStatus.CONFLICT, "L'oggetto " + assignment.getItem().getName() + " è già in un foglio di affidamento", "inventory.loanSheet.alreadyLinked").with("itemName", assignment.getItem().getName());
            }
            if (assignment.getOutstandingQuantity() < 1) {
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "L'oggetto " + assignment.getItem().getName() + " non è più in carico al socio", "inventory.loanSheet.nothingOutstanding").with("itemName", assignment.getItem().getName());
            }
        }
        return assignments;
    }

    private List<Issue> deliveryIssues(InventoryAssignment assignment, Durations parameters, ZoneId zone) {
        return LoanDurationPolicy.checkDelivery(
            assignment.getItem().getLoanType(),
            localDate(assignment.getAssignedAt(), zone),
            assignment.getExpirationDate(),
            parameters
        ).map(violation -> List.of(new Issue(assignment.getId(), violation.code(), violation.args().values()))).orElse(List.of());
    }

    private InventoryLoanSheetLine snapshotLine(InventoryAssignment assignment, int lineNumber) {
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

    private LocalDate firstDelivery(InventoryLoanSheet sheet, InventoryAssignment assignment, ZoneId zone) {
        String serial = normalizedSerial(assignment.getItem().getSerialNumber());
        if (serial == null) return localDate(assignment.getAssignedAt(), zone);
        return assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId()).stream()
            .filter(candidate -> serial.equals(normalizedSerial(candidate.getItem().getSerialNumber())))
            .map(candidate -> localDate(candidate.getAssignedAt(), zone))
            .min(Comparator.naturalOrder())
            .orElse(localDate(assignment.getAssignedAt(), zone));
    }

    private static String normalizedSerial(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    static InventoryLoanHolderRole suggestedRole(LocalDate birthDate, LocalDate on) {
        if (birthDate == null || on == null) return null;
        return Period.between(birthDate, on).getYears() >= 18 ? InventoryLoanHolderRole.MEMBER : InventoryLoanHolderRole.GUARDIAN;
    }

    private InventoryLoanSheet requiredSheet(long id) {
        return sheetRepository.findByIdAndDeletedFalse(id).orElseThrow(() -> notFound("Foglio di affidamento non trovato", "inventory.loanSheet.notFound"));
    }

    private InventoryLoanSheet requiredIssuedSheetForUpdate(long id) {
        InventoryLoanSheet sheet = sheetRepository.findForUpdate(id).orElseThrow(() -> notFound("Foglio di affidamento non trovato", "inventory.loanSheet.notFound"));
        if (sheet.getStatus() != InventoryLoanSheetStatus.ISSUED) {
            throw error(HttpStatus.CONFLICT, "L'operazione è disponibile solo per un foglio emesso", "inventory.loanSheet.notIssued");
        }
        return sheet;
    }

    private InventoryLoanSheet issuedSheetOf(InventoryAssignment assignment, String message) {
        if (assignment.getLoanSheetId() == null) throw error(HttpStatus.CONFLICT, message, "inventory.loanSheet.notLinked");
        InventoryLoanSheet sheet = sheetRepository.findForUpdate(assignment.getLoanSheetId())
            .orElseThrow(() -> error(HttpStatus.CONFLICT, message, "inventory.loanSheet.notLinked"));
        if (sheet.getStatus() != InventoryLoanSheetStatus.ISSUED) throw error(HttpStatus.CONFLICT, message, "inventory.loanSheet.notIssued");
        return sheet;
    }

    private UsersDTO requiredUser(Long userIndex, AbstractAuthenticationToken token) {
        return usersService.findOne(userIndex, token).orElseThrow(() -> notFound("Utente non trovato nel tenant", "inventory.user.notFound"));
    }

    private Summary toSummary(InventoryLoanSheet sheet) {
        return new Summary(
            sheet.getId(),
            sheet.getSheetNumber(),
            sheet.getStatus(),
            sheet.getIssuedOn(),
            sheet.getUserIndex(),
            sheet.getMemberName(),
            sheet.getMemberLastName(),
            sheet.getHolderRole(),
            InventoryLoanTemplateService.label(sheet.getTemplate()),
            sheet.getReturnOutcome(),
            sheet.getClosedAt(),
            sheet.getSettledAt(),
            sheet.getCancelledAt(),
            sheet.getPersonalDataPurgedAt() != null,
            closable(sheet),
            sheet.getEntityVersion()
        );
    }

    private Detail toDetail(InventoryLoanSheet sheet) {
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        List<InventoryLoanSheetLine> lines = lineRepository.findAllBySheet_IdAndDeletedFalseOrderByLineNumberAsc(sheet.getId());
        Map<Long, InventoryLoanSheetLine> linesByAssignment = lines.stream()
            .collect(Collectors.toMap(InventoryLoanSheetLine::getAssignmentId, Function.identity(), (first, second) -> first));
        List<LinkedAssignment> linked = assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId()).stream()
            .map(assignment -> new LinkedAssignment(
                assignment.getId(),
                assignment.getItem().getId(),
                assignment.getItem().getName(),
                assignment.getItem().getInventoryNumber(),
                assignment.getItem().getLoanType(),
                assignment.getAssignedQuantity(),
                assignment.getOutstandingQuantity(),
                assignment.getStatus(),
                assignment.getExpirationDate(),
                !linesByAssignment.containsKey(assignment.getId()),
                assignment.getReplacesAssignmentId()
            ))
            .toList();
        List<RegisterEntry> register = sheet.getStatus() == InventoryLoanSheetStatus.CANCELLED ? List.of() : register(sheet, lines, zone).register();
        return new Detail(
            toSummary(sheet),
            sheet.getPlace(),
            sheet.getAuthorizationReference(),
            sheet.getAuthorizationDate(),
            sheet.getAssociationSignatoryName(),
            sheet.getChargeAmount(),
            sheet.getChargeDueDate(),
            sheet.getReceivedByName(),
            sheet.getCancellationReason(),
            terms(sheet),
            lines.stream().map(InventoryLoanSheetService::toLine).toList(),
            register,
            linked
        );
    }

    private static Terms terms(InventoryLoanSheet sheet) {
        return new Terms(
            sheet.getTemplate().getId(),
            InventoryLoanTemplateService.label(sheet.getTemplate()),
            sheet.getMinMonths(),
            sheet.getMaxMonths(),
            sheet.getRenewalMaxMonths(),
            sheet.getTotalMaxMonths()
        );
    }

    private static Line toLine(InventoryLoanSheetLine line) {
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
}
