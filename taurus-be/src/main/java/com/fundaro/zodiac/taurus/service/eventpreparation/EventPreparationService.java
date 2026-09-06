package com.fundaro.zodiac.taurus.service.eventpreparation;

import static com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.*;

import com.fundaro.zodiac.taurus.domain.*;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.eventpreparation.*;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.inventory.*;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.eventpreparation.*;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.inventory.*;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EventPreparationService {
    private static final String ENTITY = "eventPreparation";
    private static final Set<InventoryAssignmentStatus> ACTIVE_ASSIGNMENTS = EnumSet.of(InventoryAssignmentStatus.ACTIVE, InventoryAssignmentStatus.PARTIALLY_RETURNED);

    private final CalendarEventsRepository events;
    private final EventPreparationRepository preparations;
    private final EventProgramEntryRepository programs;
    private final EventMaterialRepository materials;
    private final TracksRepository tracks;
    private final InventoryItemRepository inventoryItems;
    private final InventoryAssignmentRepository assignments;
    private final UsersRepository users;
    private final FinancialMovementRepository movements;

    public EventPreparationService(
        CalendarEventsRepository events,
        EventPreparationRepository preparations,
        EventProgramEntryRepository programs,
        EventMaterialRepository materials,
        TracksRepository tracks,
        InventoryItemRepository inventoryItems,
        InventoryAssignmentRepository assignments,
        UsersRepository users,
        FinancialMovementRepository movements
    ) {
        this.events = events;
        this.preparations = preparations;
        this.programs = programs;
        this.materials = materials;
        this.tracks = tracks;
        this.inventoryItems = inventoryItems;
        this.assignments = assignments;
        this.users = users;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public View get(Long eventId) {
        CalendarEvents event = event(eventId);
        CalendarEventPreparation preparation = preparations.findByEvent_IdAndDeletedFalse(eventId).orElse(null);
        if (preparation == null) return new View(null, notConfigured(event), List.of(), availability(event, null), List.of());
        List<CalendarEventProgramEntry> program = programs.findCurrent(eventId);
        List<CalendarEventMaterial> materialRows = materials.findCurrent(eventId);
        return new View(configuration(preparation), evaluate(event, preparation, program, materialRows), program.stream().map(this::programDto).toList(), availability(event, preparation), materialRows.stream().map(this::materialDto).toList());
    }

    public View configure(Long eventId, Configuration request, AbstractAuthenticationToken token) {
        validate(request);
        CalendarEvents event = event(eventId);
        CalendarEventPreparation preparation = preparations.findByEvent_IdAndDeletedFalse(eventId).orElseGet(() -> {
            CalendarEventPreparation value = new CalendarEventPreparation();
            value.setEvent(event);
            value.initializeAudit(actor(token));
            return value;
        });
        if (preparation.getId() != null && preparation.getEntityVersion() != request.version()) conflict();
        apply(preparation, request);
        preparation.touchAudit(actor(token));
        preparations.save(preparation);
        return get(eventId);
    }

    public View replaceProgram(Long eventId, ProgramRequest request, AbstractAuthenticationToken token) {
        event(eventId);
        List<CalendarEventProgramEntry> old = programs.findCurrent(eventId);
        old.forEach(value -> { value.setDeleted(true); value.touchAudit(actor(token)); });
        programs.saveAll(old);
        programs.flush();
        CalendarEvents event = event(eventId);
        int order = 0;
        List<CalendarEventProgramEntry> added = new ArrayList<>();
        for (ProgramEntryRequest row : request.entries()) {
            Tracks track = tracks.findByIdAndDeletedFalse(row.trackId()).orElseThrow(() -> invalid("Traccia non trovata"));
            CalendarEventProgramEntry entry = new CalendarEventProgramEntry();
            entry.setEvent(event); entry.setTrack(track); entry.setDisplayOrder(order++);
            entry.setPlannedDurationSeconds(row.plannedDurationSeconds()); entry.setNotes(trim(row.notes()));
            entry.initializeAudit(actor(token)); added.add(entry);
        }
        programs.saveAll(added);
        return get(eventId);
    }

    public View replaceMaterials(Long eventId, MaterialsRequest request, AbstractAuthenticationToken token) {
        CalendarEvents event = event(eventId);
        List<CalendarEventMaterial> old = materials.findCurrent(eventId);
        old.forEach(value -> { value.setDeleted(true); value.touchAudit(actor(token)); });
        materials.saveAll(old);
        materials.flush();
        int order = 0;
        List<CalendarEventMaterial> added = new ArrayList<>();
        for (MaterialRequest row : request.materials()) {
            InventoryItem item = inventoryItems.findByIdAndDeletedFalse(row.itemId()).orElseThrow(() -> invalid("Oggetto inventario non trovato"));
            InventoryAssignment assignment = row.assignmentId() == null ? null : assignments.findByIdAndDeletedFalse(row.assignmentId()).orElseThrow(() -> invalid("Assegnazione non trovata"));
            if (assignment != null && !assignment.getItem().getId().equals(item.getId())) throw invalid("L'assegnazione non appartiene all'oggetto selezionato");
            CalendarEventMaterial material = new CalendarEventMaterial();
            material.setEvent(event); material.setItem(item); material.setAssignment(assignment);
            material.setRequiredQuantity(row.requiredQuantity()); material.setDisplayOrder(order++); material.setNotes(trim(row.notes()));
            material.initializeAudit(actor(token)); added.add(material);
        }
        materials.saveAll(added);
        return get(eventId);
    }

    public View confirmMaterial(Long eventId, Long materialId, AbstractAuthenticationToken token) {
        CalendarEventMaterial material = materials.findCurrentById(eventId, materialId).orElseThrow(() -> invalid("Materiale non trovato"));
        material.setConfirmationHash(materialHash(material));
        material.setConfirmationAt(ZonedDateTime.now()); material.setConfirmationBy(actor(token)); material.touchAudit(actor(token));
        materials.save(material);
        return get(eventId);
    }

    public View confirmBudget(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = event(eventId); CalendarEventPreparation preparation = requiredPreparation(eventId);
        preparation.setBudgetConfirmationHash(budgetHash(event)); preparation.setBudgetConfirmationAt(ZonedDateTime.now()); preparation.setBudgetConfirmationBy(actor(token));
        preparation.touchAudit(actor(token)); preparations.save(preparation); return get(eventId);
    }

    public View confirmPresence(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = event(eventId); CalendarEventPreparation preparation = requiredPreparation(eventId);
        preparation.setPresenceConfirmationHash(presenceHash(event)); preparation.setPresenceConfirmationAt(ZonedDateTime.now()); preparation.setPresenceConfirmationBy(actor(token));
        preparation.touchAudit(actor(token)); preparations.save(preparation); return get(eventId);
    }

    public View confirmNoMovements(Long eventId, AbstractAuthenticationToken token) {
        CalendarEvents event = event(eventId); CalendarEventPreparation preparation = requiredPreparation(eventId);
        List<FinancialMovement> current = movements.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(eventId);
        if (!current.isEmpty()) throw invalid("Sono presenti movimenti economici");
        preparation.setNoMovementsConfirmationHash(financialHash(event, current)); preparation.setNoMovementsConfirmationAt(ZonedDateTime.now()); preparation.setNoMovementsConfirmationBy(actor(token));
        preparation.touchAudit(actor(token)); preparations.save(preparation); return get(eventId);
    }

    private Evaluation evaluate(CalendarEvents event, CalendarEventPreparation preparation, List<CalendarEventProgramEntry> program, List<CalendarEventMaterial> materialRows) {
        Phase phase = phase(event); Checks pre = new Checks();
        pre.check("EVENT_DATA", "EVENT", validEvent(event, preparation), eventDataSeverity(event, preparation), "Completa i dati e la visibilità dell’evento", "#event-data");
        if (preparation.isProgramRequired()) pre.check("PROGRAM_MISSING", "PROGRAM", !program.isEmpty() && validProgram(event, program), Severity.BLOCKER, "Definisci un programma pubblicabile", "#preparation-program");
        if (programExceedsEventDuration(event, program)) pre.warn("PROGRAM_TOO_LONG", "PROGRAM", "La durata pianificata supera quella dell’evento", "#preparation-program");
        if (preparation.isScoresRequired()) pre.check("SCORE_NOT_READY", "SCORES", scoresReady(event, program), Severity.BLOCKER, "Completa gli spartiti e i relativi file", "#preparation-program");
        Availability availability = availability(event, preparation);
        if (preparation.isAvailabilityRequired()) {
            boolean expired = availability.deadline() != null && !ZonedDateTime.now().isBefore(availability.deadline());
            boolean ready = availability.available() >= preparation.getMinimumAvailableParticipants() && availability.missing() == 0;
            pre.check("AVAILABILITY_INCOMPLETE", "AVAILABILITY", ready, expired ? Severity.BLOCKER : Severity.WARNING, "Raccogli le disponibilità richieste", "#availability");
        }
        if (preparation.isMaterialsRequired()) pre.check("MATERIAL_NOT_READY", "MATERIALS", materialsReady(materialRows), materialsExpired(event, preparation) ? Severity.BLOCKER : Severity.WARNING, "Assegna e conferma i materiali", "#preparation-materials");
        if (preparation.isBudgetRequired()) pre.check("BUDGET_NOT_CONFIRMED", "BUDGET", Objects.equals(preparation.getBudgetConfirmationHash(), budgetHash(event)), Severity.BLOCKER, "Verifica e conferma il preventivo", "#preparation-budget");

        Checks close = new Checks();
        if (phase == Phase.FOLLOW_UP && preparation.isPresenceClosureRequired()) close.check("PRESENCE_NOT_CONFIRMED", "PRESENCE", Objects.equals(preparation.getPresenceConfirmationHash(), presenceHash(event)), Severity.BLOCKER, "Verifica il registro presenze", "#presence");
        if (phase == Phase.FOLLOW_UP && preparation.isFinancialClosureRequired()) close.check("FINANCE_NOT_CLOSED", "FINANCE", financeClosed(event, preparation), Severity.BLOCKER, "Chiudi la posizione economica", "#preparation-budget");
        if (phase == Phase.FOLLOW_UP && preparation.isFinancialClosureRequired() && hasUnreconciledMovements(event.getId())) close.warn("MOVEMENTS_UNRECONCILED", "FINANCE", "Restano movimenti non riconciliati", "#preparation-budget");

        List<Issue> issues = new ArrayList<>(pre.issues); issues.addAll(close.issues);
        PreparationStatus preparationStatus = phase == Phase.UNKNOWN ? PreparationStatus.UNKNOWN : pre.blockers() > 0 ? PreparationStatus.BLOCKED : pre.warnings() > 0 ? PreparationStatus.ATTENTION : PreparationStatus.READY;
        ClosureStatus closureStatus = phase == Phase.UNKNOWN ? ClosureStatus.UNKNOWN : phase != Phase.FOLLOW_UP || close.applicable == 0 ? ClosureStatus.NOT_REQUIRED : close.blockers() > 0 ? ClosureStatus.TO_CLOSE : close.warnings() > 0 ? ClosureStatus.CLOSED_WITH_WARNINGS : ClosureStatus.CLOSED;
        int applicable = pre.applicable + close.applicable; int passed = pre.passed + close.passed;
        return new Evaluation(ZonedDateTime.now(), phase, preparationStatus, closureStatus, applicable == 0 ? 100 : Math.round(passed * 100f / applicable), passed, applicable, (int) issues.stream().filter(i -> i.severity() == Severity.BLOCKER).count(), (int) issues.stream().filter(i -> i.severity() == Severity.WARNING).count(), issues);
    }

    private boolean validEvent(CalendarEvents event, CalendarEventPreparation p) {
        return event.getName() != null && !event.getName().isBlank() && event.getStartDate() != null && event.getEndDate() != null && event.getEndDate().after(event.getStartDate()) && (!p.isLocationRequired() || event.getLocation() != null && !event.getLocation().isBlank()) && event.getState() != StateEnum.DRAFT;
    }
    private Severity eventDataSeverity(CalendarEvents event, CalendarEventPreparation p) {
        boolean datesValid = event.getStartDate() != null && event.getEndDate() != null && event.getEndDate().after(event.getStartDate());
        boolean otherDataValid = event.getName() != null && !event.getName().isBlank() && (!p.isLocationRequired() || event.getLocation() != null && !event.getLocation().isBlank());
        if (datesValid && otherDataValid && event.getState() == StateEnum.DRAFT && event.getStartDate().toInstant().isAfter(Instant.now().plus(Duration.ofDays(7)))) return Severity.WARNING;
        return Severity.BLOCKER;
    }
    private boolean programExceedsEventDuration(CalendarEvents event, List<CalendarEventProgramEntry> rows) {
        if (event.getStartDate() == null || event.getEndDate() == null || rows.isEmpty() || rows.stream().anyMatch(row -> row.getPlannedDurationSeconds() == null)) return false;
        long planned = rows.stream().mapToLong(CalendarEventProgramEntry::getPlannedDurationSeconds).sum();
        return planned > Duration.between(event.getStartDate().toInstant(), event.getEndDate().toInstant()).toSeconds();
    }
    private boolean validProgram(CalendarEvents event, List<CalendarEventProgramEntry> rows) {
        for (int i = 0; i < rows.size(); i++) {
            CalendarEventProgramEntry row = rows.get(i); StateEnum state = row.getTrack().getState();
            if (row.getDisplayOrder() != i || state == StateEnum.DRAFT || event.getState() == StateEnum.PUBLIC && state != StateEnum.PUBLIC) return false;
        }
        return true;
    }
    private boolean scoresReady(CalendarEvents event, List<CalendarEventProgramEntry> rows) {
        if (rows.isEmpty()) return false;
        for (CalendarEventProgramEntry row : rows) {
            boolean ready = row.getTrack().getScores().stream().anyMatch(score -> !Boolean.TRUE.equals(score.getNeedsReview()) && !score.getInstruments().isEmpty() && score.getMedia().stream().anyMatch(media -> media.getStatus() == MediaAssetStatus.READY));
            if (!ready || event.getState() == StateEnum.PUBLIC && row.getTrack().getState() != StateEnum.PUBLIC) return false;
        }
        return true;
    }
    private boolean materialsReady(List<CalendarEventMaterial> rows) { return !rows.isEmpty() && rows.stream().allMatch(this::materialReady); }
    private boolean materialReady(CalendarEventMaterial row) {
        InventoryAssignment assignment = row.getAssignment();
        return assignment != null && ACTIVE_ASSIGNMENTS.contains(assignment.getStatus()) && assignment.getOutstandingQuantity() >= row.getRequiredQuantity() && assignment.getItem().getId().equals(row.getItem().getId()) && row.getItem().getConditionStatus() != InventoryCondition.TO_REPAIR && row.getItem().getConditionStatus() != InventoryCondition.OUT_OF_SERVICE && Objects.equals(row.getConfirmationHash(), materialHash(row));
    }
    private boolean financeClosed(CalendarEvents event, CalendarEventPreparation preparation) {
        List<FinancialMovement> current = movements.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(event.getId());
        BigDecimal fee = amount(event.getFee()); BigDecimal costs = event.getCosts().stream().map(EventCost::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = current.stream().filter(m -> m.getDirection() == FinancialDirection.INCOME).map(FinancialMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expense = current.stream().filter(m -> m.getDirection() == FinancialDirection.EXPENSE).map(FinancialMovement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean settled = (!current.isEmpty() || fee.signum() != 0 || costs.signum() != 0) && fee.subtract(income).signum() == 0 && costs.subtract(expense).signum() == 0;
        return settled || current.isEmpty() && Objects.equals(preparation.getNoMovementsConfirmationHash(), financialHash(event, current));
    }
    private boolean hasUnreconciledMovements(Long eventId) {
        return movements.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(eventId).stream().anyMatch(value -> !value.isReconciled());
    }

    private Availability availability(CalendarEvents event, CalendarEventPreparation preparation) {
        Set<String> expected = event.getState() == StateEnum.PUBLIC ? new HashSet<>(users.findActiveKeycloakIdsByRolesIn(List.of(RoleEnum.ROLE_USER, RoleEnum.ROLE_USER_EXTERNAL))) : event.getState() == StateEnum.COMPLETE ? new HashSet<>(users.findActiveKeycloakIdsByRolesIn(List.of(RoleEnum.ROLE_USER))) : Set.of();
        int available = 0, unavailable = 0;
        for (CalendarEventAvailability response : event.getAvailabilities()) if (expected.contains(response.getUser().getKeycloakId())) { if (response.getAvailability() == CalendarEventAvailability.Availability.AVAILABLE) available++; else unavailable++; }
        ZonedDateTime deadline = preparation == null || event.getStartDate() == null ? null : ZonedDateTime.ofInstant(event.getStartDate().toInstant(), ZoneId.systemDefault()).minusMinutes(preparation.getAvailabilityDeadlineMinutes());
        return new Availability(expected.size(), available, unavailable, Math.max(0, expected.size() - available - unavailable), preparation == null ? null : preparation.getMinimumAvailableParticipants(), deadline);
    }
    private boolean materialsExpired(CalendarEvents event, CalendarEventPreparation p) { return event.getStartDate() != null && Instant.now().isAfter(event.getStartDate().toInstant().minusSeconds(p.getMaterialsDeadlineMinutes() * 60L)); }
    private Phase phase(CalendarEvents event) { if (event.getStartDate() == null || event.getEndDate() == null) return Phase.UNKNOWN; Instant now = Instant.now(); return now.isBefore(event.getStartDate().toInstant()) ? Phase.PREPARATION : now.isAfter(event.getEndDate().toInstant()) ? Phase.FOLLOW_UP : Phase.IN_PROGRESS; }

    private Evaluation notConfigured(CalendarEvents event) { return new Evaluation(ZonedDateTime.now(), phase(event), PreparationStatus.NOT_CONFIGURED, ClosureStatus.NOT_REQUIRED, 0, 0, 0, 0, 0, List.of()); }
    private ProgramEntry programDto(CalendarEventProgramEntry row) { return new ProgramEntry(row.getId(), row.getTrack().getId(), row.getTrack().getName(), row.getTrack().getState().name(), row.getDisplayOrder(), row.getPlannedDurationSeconds(), row.getNotes()); }
    private Material materialDto(CalendarEventMaterial row) { return new Material(row.getId(), row.getItem().getId(), row.getItem().getName(), row.getAssignment() == null ? null : row.getAssignment().getId(), row.getAssignment() == null ? null : row.getAssignment().getUserName() + " " + row.getAssignment().getUserLastName(), row.getRequiredQuantity(), row.getItem().getConditionStatus().name(), materialReady(row), row.getNotes()); }
    private Configuration configuration(CalendarEventPreparation p) { return new Configuration(p.getProfile(), p.isLocationRequired(), p.isProgramRequired(), p.isScoresRequired(), p.isAvailabilityRequired(), p.getMinimumAvailableParticipants(), p.getAvailabilityDeadlineMinutes(), p.isMaterialsRequired(), p.getMaterialsDeadlineMinutes(), p.isBudgetRequired(), p.isPresenceClosureRequired(), p.isFinancialClosureRequired(), p.getEntityVersion()); }
    private void apply(CalendarEventPreparation p, Configuration c) { p.setProfile(c.profile()); p.setLocationRequired(c.locationRequired()); p.setProgramRequired(c.programRequired()); p.setScoresRequired(c.scoresRequired()); p.setAvailabilityRequired(c.availabilityRequired()); p.setMinimumAvailableParticipants(c.availabilityRequired() ? c.minimumAvailableParticipants() : null); p.setAvailabilityDeadlineMinutes(c.availabilityDeadlineMinutes()); p.setMaterialsRequired(c.materialsRequired()); p.setMaterialsDeadlineMinutes(c.materialsDeadlineMinutes()); p.setBudgetRequired(c.budgetRequired()); p.setPresenceClosureRequired(c.presenceClosureRequired()); p.setFinancialClosureRequired(c.financialClosureRequired()); }
    private void validate(Configuration c) { if (c.scoresRequired() && !c.programRequired()) throw invalid("Gli spartiti richiedono il programma"); if (c.availabilityRequired() && (c.minimumAvailableParticipants() == null || c.minimumAvailableParticipants() < 1)) throw invalid("Indica il numero minimo di partecipanti"); }

    private String materialHash(CalendarEventMaterial m) { InventoryAssignment a = m.getAssignment(); return hash(m.getItem().getId(), a == null ? null : a.getId(), a == null ? null : a.getOutstandingQuantity(), a == null ? null : a.getStatus(), m.getItem().getConditionStatus(), m.getRequiredQuantity()); }
    private String budgetHash(CalendarEvents e) { List<Object> values = new ArrayList<>(); values.add(amount(e.getFee())); e.getCosts().forEach(c -> { values.add(c.getDescription()); values.add(amount(c.getAmount())); }); return hash(values.toArray()); }
    private String presenceHash(CalendarEvents e) { List<Object> values = new ArrayList<>(); e.getPresences().forEach(p -> { values.add(p.getUser().getId()); values.add(p.getArrivalTime()); values.add(p.getNote()); }); return hash(values.toArray()); }
    private String financialHash(CalendarEvents e, List<FinancialMovement> rows) { List<Object> values = new ArrayList<>(); values.add(budgetHash(e)); rows.forEach(m -> { values.add(m.getId()); values.add(m.getDirection()); values.add(m.getAmount()); values.add(m.getBookingDate()); }); return hash(values.toArray()); }
    private static String hash(Object... values) { try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] result = digest.digest(Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(result); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static BigDecimal amount(BigDecimal value) { return value == null ? BigDecimal.ZERO : value.stripTrailingZeros(); }
    private CalendarEvents event(Long id) { CalendarEvents value = events.findByIdAndDeletedFalse(id).orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Evento non trovato", ENTITY, "notFound")); if (Boolean.TRUE.equals(value.getSeriesExcluded())) throw new RequestAlertException(HttpStatus.NOT_FOUND, "Evento non trovato", ENTITY, "notFound"); return value; }
    private CalendarEventPreparation requiredPreparation(Long id) { return preparations.findByEvent_IdAndDeletedFalse(id).orElseThrow(() -> invalid("Configura prima la preparazione")); }
    private static String actor(AbstractAuthenticationToken token) { return token == null || token.getName() == null ? "system" : token.getName(); }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static RequestAlertException invalid(String message) { return new RequestAlertException(HttpStatus.BAD_REQUEST, message, ENTITY, "invalid"); }
    private static void conflict() { throw new RequestAlertException(HttpStatus.CONFLICT, "La configurazione è stata modificata", ENTITY, "concurrentModification"); }

    private static final class Checks {
        private int applicable; private int passed; private final List<Issue> issues = new ArrayList<>();
        void check(String code, String area, boolean success, Severity severity, String message, String action) { applicable++; if (success) passed++; else issues.add(new Issue(code, area, severity, message, action)); }
        void warn(String code, String area, String message, String action) { issues.add(new Issue(code, area, Severity.WARNING, message, action)); }
        int blockers() { return (int) issues.stream().filter(i -> i.severity() == Severity.BLOCKER).count(); }
        int warnings() { return (int) issues.stream().filter(i -> i.severity() == Severity.WARNING).count(); }
    }
}
