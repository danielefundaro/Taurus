package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYear;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccount;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementAttachment;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.finance.AccountingYearRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialAccountRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementAttachmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AttachmentDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.DashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventBudgetRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventCostDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventCostRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.ReconciliationRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class FinanceService {

    private static final String ENTITY = "finance";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final long MAX_ATTACHMENT_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ATTACHMENTS = 20;

    private final AccountingYearRepository yearRepository;
    private final FinancialAccountRepository accountRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FinancialMovementRepository movementRepository;
    private final FinancialMovementAttachmentRepository attachmentRepository;
    private final CalendarEventsRepository eventRepository;
    private final MediaRepository mediaRepository;
    private final MediaService mediaService;

    public FinanceService(
        AccountingYearRepository yearRepository,
        FinancialAccountRepository accountRepository,
        FinancialCategoryRepository categoryRepository,
        FinancialMovementRepository movementRepository,
        FinancialMovementAttachmentRepository attachmentRepository,
        CalendarEventsRepository eventRepository,
        MediaRepository mediaRepository,
        MediaService mediaService
    ) {
        this.yearRepository = yearRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.attachmentRepository = attachmentRepository;
        this.eventRepository = eventRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public List<AccountDTO> findAccounts(boolean includeArchived, AbstractAuthenticationToken token) {
        tenant(token);
        LocalDate today = LocalDate.now();
        List<FinancialAccount> accounts = includeArchived
            ? accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()
            : accountRepository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();
        return accounts.stream().map(account -> toAccountDto(account, balance(account.getId(), today))).toList();
    }

    @Transactional(readOnly = true)
    public AccountDTO findAccount(long id, LocalDate balanceDate, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        return toAccountDto(account, balance(id, Objects.requireNonNullElse(balanceDate, LocalDate.now())));
    }

    public AccountDTO createAccount(AccountRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String name = request.name().trim();
        if (accountRepository.existsByNameIgnoreCaseAndDeletedFalseAndActiveTrue(name)) {
            throw error(HttpStatus.CONFLICT, "Esiste già un conto attivo con questo nome", "finance.account.nameExists");
        }
        FinancialAccount account = new FinancialAccount();
        account.initializeAudit(actor(token));
        applyAccount(account, request);
        accountRepository.save(account);
        if (request.initialBalance() != null && request.initialBalance().signum() != 0) {
            LocalDate openingDate = Objects.requireNonNullElse(request.initialBalanceDate(), LocalDate.now());
            AccountingYear year = ensureYear(openingDate.getYear(), actor(token));
            FinancialMovement opening = new FinancialMovement();
            opening.initializeAudit(actor(token));
            opening.setAccountingYear(year);
            opening.setAccount(account);
            opening.setDirection(request.initialBalance().signum() > 0 ? FinancialDirection.INCOME : FinancialDirection.EXPENSE);
            opening.setNature(FinancialMovementNature.OPENING);
            opening.setBookingDate(openingDate);
            opening.setAmount(request.initialBalance().abs());
            opening.setCurrency(account.getCurrency());
            opening.setDescription("Saldo iniziale");
            movementRepository.save(opening);
        }
        return toAccountDto(account, balance(account.getId(), LocalDate.now()));
    }

    public AccountDTO updateAccount(long id, AccountRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        boolean reactivated = !account.isActive();
        if (accountRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalseAndActiveTrue(request.name().trim(), id)) {
            throw error(HttpStatus.CONFLICT, "Esiste già un conto attivo con questo nome", "finance.account.nameExists");
        }
        applyAccount(account, request);
        account.touchAudit(actor(token));
        accountRepository.save(account);
        return toAccountDto(account, balance(id, LocalDate.now()));
    }

    public void archiveAccount(long id, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        account.setActive(false);
        account.touchAudit(actor(token));
        accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> findCategories(boolean includeArchived, AbstractAuthenticationToken token) {
        tenant(token);
        List<FinancialCategory> categories = includeArchived
            ? categoryRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()
            : categoryRepository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();
        return categories.stream().map(this::toCategoryDto).toList();
    }

    public CategoryDTO createCategory(CategoryRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndActiveTrue(request.name().trim())) {
            throw error(HttpStatus.CONFLICT, "Esiste già una categoria attiva con questo nome", "finance.category.nameExists");
        }
        FinancialCategory category = new FinancialCategory();
        category.initializeAudit(actor(token));
        applyCategory(category, request);
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    public CategoryDTO updateCategory(long id, CategoryRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialCategory category = requiredCategory(id, false);
        boolean reactivated = !category.isActive();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalseAndActiveTrue(request.name().trim(), id)) {
            throw error(HttpStatus.CONFLICT, "Esiste già una categoria attiva con questo nome", "finance.category.nameExists");
        }
        applyCategory(category, request);
        category.touchAudit(actor(token));
        categoryRepository.save(category);
        return toCategoryDto(category);
    }

    public void archiveCategory(long id, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialCategory category = requiredCategory(id, false);
        category.setActive(false);
        category.touchAudit(actor(token));
        categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public Page<MovementDTO> findMovements(
        LocalDate from,
        LocalDate to,
        Long accountId,
        Long categoryId,
        Long eventId,
        FinancialDirection direction,
        Boolean reconciled,
        String query,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        Specification<FinancialMovement> specification = movementSpecification(from, to, accountId, categoryId, eventId, direction, reconciled, query);
        return movementRepository.findAll(specification, pageable).map(this::toMovementDto);
    }

    @Transactional(readOnly = true)
    public MovementDTO findMovement(long id, AbstractAuthenticationToken token) {
        tenant(token);
        return toMovementDto(requiredMovement(id));
    }

    public MovementDTO createMovement(MovementRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        if (request.requestKey() != null) {
            MovementDTO existing = movementRepository.findByRequestKeyAndDeletedFalse(request.requestKey()).map(this::toMovementDto).orElse(null);
            if (existing != null) return existing;
        }
        FinancialMovement movement = new FinancialMovement();
        movement.initializeAudit(actor);
        movement.setNature(FinancialMovementNature.ORDINARY);
        applyMovement(movement, request, actor);
        movementRepository.save(movement);
        recalculateFollowingYears(movement.getAccountingYear().getYear(), actor);
        return toMovementDto(movement);
    }

    public MovementDTO updateMovement(long id, MovementRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        FinancialMovement movement = requiredMovement(id);
        int oldYear = movement.getAccountingYear().getYear();
        if (movement.getNature() == FinancialMovementNature.TRANSFER) {
            updateTransferPair(movement, request, actor);
        } else {
            applyMovement(movement, request, actor);
            movement.touchAudit(actor);
            movementRepository.save(movement);
        }
        recalculateFollowingYears(Math.min(oldYear, movement.getAccountingYear().getYear()), actor);
        return toMovementDto(movement);
    }

    public void deleteMovement(long id, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        FinancialMovement movement = requiredMovement(id);
        int year = movement.getAccountingYear().getYear();
        List<FinancialMovement> targets = movement.getNature() == FinancialMovementNature.TRANSFER && movement.getTransferGroup() != null
            ? movementRepository.findAllByTransferGroupAndDeletedFalse(movement.getTransferGroup())
            : List.of(movement);
        targets.forEach(target -> {
            target.setDeleted(true);
            target.touchAudit(actor);
        });
        movementRepository.saveAll(targets);
        recalculateFollowingYears(year, actor);
    }

    public MovementDTO reconcile(long id, ReconciliationRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        FinancialMovement movement = requiredMovement(id);
        movement.setReconciled(request.reconciled());
        movement.setReconciledAt(request.reconciled() ? ZonedDateTime.now() : null);
        movement.setReconciledBy(request.reconciled() ? actor : null);
        movement.setReconciliationReference(request.reconciled() ? trimToNull(request.reference()) : null);
        movement.touchAudit(actor);
        movementRepository.save(movement);
        return toMovementDto(movement);
    }

    public TransferDTO createTransfer(TransferRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw error(HttpStatus.BAD_REQUEST, "Il conto di origine e quello di destinazione devono essere diversi", "finance.transfer.sameAccount");
        }
        FinancialAccount source = requiredAccount(request.sourceAccountId(), true);
        FinancialAccount destination = requiredAccount(request.destinationAccountId(), true);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw error(HttpStatus.BAD_REQUEST, "I conti del trasferimento devono avere la stessa valuta", "finance.transfer.currencyMismatch");
        }
        UUID group = UUID.randomUUID();
        AccountingYear year = ensureYear(request.bookingDate().getYear(), actor);
        FinancialMovement outgoing = transferLeg(source, year, FinancialDirection.EXPENSE, request, group, actor);
        FinancialMovement incoming = transferLeg(destination, year, FinancialDirection.INCOME, request, group, actor);
        movementRepository.saveAll(List.of(outgoing, incoming));
        recalculateFollowingYears(year.getYear(), actor);
        return new TransferDTO(group, toMovementDto(outgoing), toMovementDto(incoming));
    }

    @Transactional(readOnly = true)
    public DashboardDTO dashboard(LocalDate from, LocalDate to, AbstractAuthenticationToken token) {
        tenant(token);
        LocalDate effectiveTo = Objects.requireNonNullElse(to, LocalDate.now());
        LocalDate effectiveFrom = Objects.requireNonNullElse(from, effectiveTo.withDayOfYear(1));
        List<FinancialMovement> movements = movementRepository.findAllByDeletedFalseAndBookingDateBetween(effectiveFrom, effectiveTo);
        BigDecimal income = total(movements, FinancialDirection.INCOME, true);
        BigDecimal expense = total(movements, FinancialDirection.EXPENSE, true);
        long unreconciled = movements.stream().filter(movement -> movement.getNature() == FinancialMovementNature.ORDINARY && !movement.isReconciled()).count();
        List<AccountDTO> accounts = accountRepository.findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc().stream()
            .map(account -> toAccountDto(account, balance(account.getId(), effectiveTo)))
            .toList();
        BigDecimal totalBalance = accounts.stream().map(AccountDTO::balance).reduce(ZERO, BigDecimal::add);
        return new DashboardDTO(totalBalance, income, expense, income.subtract(expense), movements.size(), unreconciled, accounts);
    }

    @Transactional(readOnly = true)
    public BigDecimal accountBalance(long accountId, LocalDate date, AbstractAuthenticationToken token) {
        tenant(token);
        requiredAccount(accountId, false);
        return balance(accountId, Objects.requireNonNullElse(date, LocalDate.now()));
    }

    public AttachmentDTO addAttachment(long movementId, MultipartFile file, String description, AbstractAuthenticationToken token) throws IOException {
        tenant(token);
        if (file == null || file.isEmpty() || file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Ogni allegato deve avere dimensione massima di 10 MB", "finance.attachment.tooLarge");
        }
        if (attachmentRepository.countByMovement_IdAndDeletedFalseAndActiveTrue(movementId) >= MAX_ATTACHMENTS) {
            throw error(HttpStatus.CONFLICT, "Sono consentiti al massimo 20 allegati per movimento", "finance.attachment.limit");
        }
        String mime = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!List.of("application/pdf", "image/jpeg", "image/png").contains(mime)) {
            throw error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Sono supportati soltanto PDF, JPEG e PNG", "finance.attachment.unsupported");
        }
        FinancialMovement movement = requiredMovement(movementId);
        MediaDTO stored = mediaService.store(file.getBytes(), file.getOriginalFilename(), mime, "finance", token);
        FinancialMovementAttachment attachment = new FinancialMovementAttachment();
        attachment.initializeAudit(actor(token));
        attachment.setMovement(movement);
        attachment.setMediaAsset(mediaRepository.getReferenceById(stored.getId()));
        attachment.setDescription(trimToNull(description));
        attachmentRepository.save(attachment);
        return toAttachmentDto(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentDTO> findAttachments(long movementId, AbstractAuthenticationToken token) {
        tenant(token);
        requiredMovement(movementId);
        return attachmentRepository.findAllByMovement_IdAndDeletedFalseAndActiveTrueOrderByInsertDateAsc(movementId).stream()
            .map(this::toAttachmentDto)
            .toList();
    }

    public MediaService.MediaContent getAttachment(long attachmentId, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialMovementAttachment attachment = requiredAttachment(attachmentId);
        MediaService.MediaContent content = mediaService.getContent(attachment.getMediaAsset().getId(), token);
        return content;
    }

    public void deleteAttachment(long attachmentId, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialMovementAttachment attachment = requiredAttachment(attachmentId);
        Long mediaId = attachment.getMediaAsset().getId();
        attachment.setDeleted(true);
        attachment.setActive(false);
        attachment.touchAudit(actor(token));
        attachmentRepository.save(attachment);
        mediaService.deleteIfUnreferenced(mediaId, token);
    }

    @Transactional(readOnly = true)
    public EventSummaryDTO eventSummary(long eventId, AbstractAuthenticationToken token) {
        tenant(token);
        return eventSummary(requiredEvent(eventId));
    }

    @Transactional(readOnly = true)
    public Page<EventSummaryDTO> findEvents(Pageable pageable, AbstractAuthenticationToken token) {
        tenant(token);
        return eventRepository.findAll(pageable).map(this::eventSummary);
    }

    public EventSummaryDTO updateEventBudget(long eventId, EventBudgetRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        CalendarEvents event = requiredEvent(eventId);
        event.setFee(moneyOrZero(request.fee()));
        event.getCosts().clear();
        for (EventCostRequest item : request.costs()) {
            EventCost cost = new EventCost();
            cost.initializeAudit(actor(token));
            cost.setDescription(item.description().trim());
            cost.setAmount(moneyOrZero(item.amount()));
            event.getCosts().add(cost);
        }
        eventRepository.save(event);
        return eventSummary(event);
    }

    @Transactional(readOnly = true)
    public List<YearDTO> findYears(AbstractAuthenticationToken token) {
        tenant(token);
        return yearRepository.findAllByDeletedFalseOrderByYearAsc().stream().map(this::toYearDto).toList();
    }

    public YearDTO rollover(int year, AbstractAuthenticationToken token) {
        tenant(token);
        RolloverResult result = performRollover(year, actor(token));
        return result.year();
    }

    public YearDTO rolloverForActor(int year, String actor) {
        RolloverResult result = performRollover(year, actor);
        return result.year();
    }

    private RolloverResult performRollover(int year, String actor) {
        AccountingYear source = ensureYear(year, actor);
        if (source.getStatus() == AccountingYearStatus.ROLLED_OVER) return new RolloverResult(toYearDto(source), false);
        ensureYear(year + 1, actor);
        recalculateOpening(year + 1, actor);
        source.setStatus(AccountingYearStatus.ROLLED_OVER);
        source.setRolledOverAt(ZonedDateTime.now());
        source.setRolledOverBy(actor);
        source.touchAudit(actor);
        yearRepository.save(source);
        return new RolloverResult(toYearDto(source), true);
    }

    public YearDTO recalculate(int year, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        AccountingYear source = ensureYear(year, actor);
        recalculateFollowingYears(year, actor);
        source.setLastRecalculatedAt(ZonedDateTime.now());
        source.touchAudit(actor);
        YearDTO result = toYearDto(yearRepository.save(source));
        return result;
    }

    private void applyAccount(FinancialAccount account, AccountRequest request) {
        if (request.accountType() == FinancialAccountType.CASH && (notBlank(request.iban()) || notBlank(request.bankName()))) {
            throw error(HttpStatus.BAD_REQUEST, "IBAN e banca sono ammessi soltanto per un conto corrente", "finance.account.bankFields");
        }
        account.setName(request.name().trim());
        account.setDescription(trimToNull(request.description()));
        account.setAccountType(request.accountType());
        account.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));
        account.setIban(request.accountType() == FinancialAccountType.BANK ? trimToNull(request.iban()) : null);
        account.setBankName(request.accountType() == FinancialAccountType.BANK ? trimToNull(request.bankName()) : null);
        account.setDisplayOrder(Objects.requireNonNullElse(request.displayOrder(), 0));
        account.setActive(true);
    }

    private void applyCategory(FinancialCategory category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(trimToNull(request.description()));
        category.setDirection(request.direction());
        category.setDisplayOrder(Objects.requireNonNullElse(request.displayOrder(), 0));
        category.setActive(true);
    }

    private void applyMovement(FinancialMovement movement, MovementRequest request, String actor) {
        FinancialAccount account = requiredAccount(request.accountId(), true);
        FinancialCategory category = request.categoryId() == null ? null : requiredCategory(request.categoryId(), true);
        if (category != null && category.getDirection() != FinancialCategoryDirection.BOTH && !category.getDirection().name().equals(request.direction().name())) {
            throw error(HttpStatus.BAD_REQUEST, "La categoria non è compatibile con la direzione del movimento", "finance.movement.categoryDirection");
        }
        CalendarEvents event = request.eventId() == null ? null : requiredEvent(request.eventId());
        movement.setAccountingYear(ensureYear(request.bookingDate().getYear(), actor));
        movement.setAccount(account);
        movement.setCategory(category);
        movement.setEvent(event);
        movement.setEventNameSnapshot(event == null ? null : event.getName());
        movement.setDirection(request.direction());
        movement.setBookingDate(request.bookingDate());
        movement.setValueDate(request.valueDate());
        movement.setAmount(request.amount());
        movement.setCurrency(account.getCurrency());
        movement.setDescription(request.description().trim());
        movement.setCounterparty(trimToNull(request.counterparty()));
        movement.setDocumentReference(trimToNull(request.documentReference()));
        movement.setNotes(trimToNull(request.notes()));
        if (movement.getId() == null) movement.setRequestKey(request.requestKey());
    }

    private void updateTransferPair(FinancialMovement selected, MovementRequest request, String actor) {
        List<FinancialMovement> pair = movementRepository.findAllByTransferGroupAndDeletedFalse(selected.getTransferGroup());
        if (pair.size() != 2) throw error(HttpStatus.CONFLICT, "Il trasferimento non contiene due movimenti coerenti", "finance.transfer.inconsistent");
        FinancialMovement other = pair.stream().filter(value -> !value.getId().equals(selected.getId())).findFirst().orElseThrow();
        AccountingYear year = ensureYear(request.bookingDate().getYear(), actor);
        for (FinancialMovement movement : pair) {
            movement.setAccountingYear(year);
            movement.setBookingDate(request.bookingDate());
            movement.setValueDate(request.valueDate());
            movement.setAmount(request.amount());
            movement.setDescription(request.description().trim());
            movement.setNotes(trimToNull(request.notes()));
            movement.touchAudit(actor);
        }
        FinancialAccount selectedAccount = requiredAccount(request.accountId(), true);
        if (selectedAccount.getId().equals(other.getAccount().getId())) {
            throw error(HttpStatus.BAD_REQUEST, "Il conto di origine e quello di destinazione devono essere diversi", "finance.transfer.sameAccount");
        }
        if (!selectedAccount.getCurrency().equals(other.getAccount().getCurrency())) {
            throw error(HttpStatus.BAD_REQUEST, "I conti del trasferimento devono avere la stessa valuta", "finance.transfer.currencyMismatch");
        }
        selected.setAccount(selectedAccount);
        selected.setCurrency(selectedAccount.getCurrency());
        movementRepository.saveAll(pair);
    }

    private FinancialMovement transferLeg(
        FinancialAccount account,
        AccountingYear year,
        FinancialDirection direction,
        TransferRequest request,
        UUID group,
        String actor
    ) {
        FinancialMovement movement = new FinancialMovement();
        movement.initializeAudit(actor);
        movement.setAccountingYear(year);
        movement.setAccount(account);
        movement.setDirection(direction);
        movement.setNature(FinancialMovementNature.TRANSFER);
        movement.setBookingDate(request.bookingDate());
        movement.setValueDate(request.valueDate());
        movement.setAmount(request.amount());
        movement.setCurrency(account.getCurrency());
        movement.setDescription(request.description().trim());
        movement.setNotes(trimToNull(request.notes()));
        movement.setTransferGroup(group);
        return movement;
    }

    private AccountingYear ensureYear(int year, String actor) {
        return yearRepository.findByYearAndDeletedFalse(year).orElseGet(() -> {
            AccountingYear entity = new AccountingYear();
            entity.initializeAudit(actor);
            entity.setYear(year);
            entity.setStartDate(LocalDate.of(year, 1, 1));
            entity.setEndDate(LocalDate.of(year, 12, 31));
            entity.setStatus(AccountingYearStatus.OPEN);
            return yearRepository.save(entity);
        });
    }

    private void recalculateFollowingYears(int sourceYear, String actor) {
        int lastKnownYear = yearRepository.findAllByDeletedFalseOrderByYearAsc().stream()
            .map(AccountingYear::getYear)
            .max(Integer::compareTo)
            .orElse(sourceYear);
        for (int year = sourceYear + 1; year <= lastKnownYear; year++) {
            recalculateOpening(year, actor);
        }
    }

    private void recalculateOpening(int targetYear, String actor) {
        AccountingYear target = ensureYear(targetYear, actor);
        LocalDate previousEnd = LocalDate.of(targetYear - 1, 12, 31);
        for (FinancialAccount account : accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()) {
            BigDecimal previousBalance = balance(account.getId(), previousEnd);
            FinancialMovement opening = movementRepository
                .findByAccountingYear_YearAndAccount_IdAndNatureAndDeletedFalse(targetYear, account.getId(), FinancialMovementNature.OPENING)
                .orElse(null);
            if (previousBalance.signum() == 0) {
                if (opening != null) {
                    opening.setDeleted(true);
                    opening.touchAudit(actor);
                    movementRepository.save(opening);
                }
                continue;
            }
            if (opening == null) {
                opening = new FinancialMovement();
                opening.initializeAudit(actor);
                opening.setNature(FinancialMovementNature.OPENING);
                opening.setAccount(account);
                opening.setAccountingYear(target);
                opening.setBookingDate(LocalDate.of(targetYear, 1, 1));
                opening.setCurrency(account.getCurrency());
                opening.setDescription("Saldo iniziale " + targetYear);
            }
            opening.setDirection(previousBalance.signum() >= 0 ? FinancialDirection.INCOME : FinancialDirection.EXPENSE);
            opening.setAmount(previousBalance.abs());
            opening.touchAudit(actor);
            movementRepository.save(opening);
        }
        target.setLastRecalculatedAt(ZonedDateTime.now());
        target.touchAudit(actor);
        yearRepository.save(target);
    }

    private BigDecimal balance(long accountId, LocalDate date) {
        LocalDate from = LocalDate.of(date.getYear(), 1, 1);
        return movementRepository.findAllByDeletedFalseAndBookingDateBetween(from, date).stream()
            .filter(movement -> movement.getAccount().getId().equals(accountId))
            .map(this::signedAmount)
            .reduce(ZERO, BigDecimal::add);
    }

    private EventSummaryDTO eventSummary(CalendarEvents event) {
        BigDecimal fee = moneyOrZero(event.getFee());
        BigDecimal costs = event.getCosts().stream().map(EventCost::getAmount).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
        List<FinancialMovement> movements = movementRepository.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(event.getId());
        BigDecimal received = movements.stream().filter(value -> value.getDirection() == FinancialDirection.INCOME).map(FinancialMovement::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal paid = movements.stream().filter(value -> value.getDirection() == FinancialDirection.EXPENSE).map(FinancialMovement::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal remainingIncome = fee.subtract(received);
        BigDecimal remainingExpense = costs.subtract(paid);
        return new EventSummaryDTO(
            event.getId(),
            event.getName(),
            fee,
            costs,
            event.getCosts().stream().map(cost -> new EventCostDTO(cost.getId(), cost.getDescription(), moneyOrZero(cost.getAmount()))).toList(),
            fee.subtract(costs),
            received,
            paid,
            received.subtract(paid),
            remainingIncome,
            remainingExpense,
            economicStatus(fee, costs, movements, remainingIncome, remainingExpense),
            movements.stream().map(this::toMovementDto).toList()
        );
    }

    private static String economicStatus(
        BigDecimal fee,
        BigDecimal costs,
        List<FinancialMovement> movements,
        BigDecimal remainingIncome,
        BigDecimal remainingExpense
    ) {
        boolean hasBudget = fee.signum() != 0 || costs.signum() != 0;
        if (!hasBudget && movements.isEmpty()) return "NO_BUDGET";
        if (!hasBudget) return "UNPLANNED_MOVEMENTS";
        if (movements.isEmpty()) return "NO_MOVEMENTS";
        if (remainingIncome.signum() < 0 || remainingExpense.signum() < 0) return "OVERPAID_OR_OVERRUN";
        if (remainingIncome.signum() == 0 && remainingExpense.signum() == 0) return "SETTLED";
        return "PARTIALLY_SETTLED";
    }

    private Specification<FinancialMovement> movementSpecification(
        LocalDate from,
        LocalDate to,
        Long accountId,
        Long categoryId,
        Long eventId,
        FinancialDirection direction,
        Boolean reconciled,
        String queryText
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("bookingDate"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("bookingDate"), to));
            if (accountId != null) predicates.add(builder.equal(root.get("account").get("id"), accountId));
            if (categoryId != null) predicates.add(builder.equal(root.get("category").get("id"), categoryId));
            if (eventId != null) predicates.add(builder.equal(root.get("event").get("id"), eventId));
            if (direction != null) predicates.add(builder.equal(root.get("direction"), direction));
            if (reconciled != null) predicates.add(builder.equal(root.get("reconciled"), reconciled));
            if (notBlank(queryText)) {
                String pattern = "%" + queryText.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("description")), pattern),
                    builder.like(builder.lower(root.get("counterparty")), pattern),
                    builder.like(builder.lower(root.get("documentReference")), pattern)
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private BigDecimal total(List<FinancialMovement> movements, FinancialDirection direction, boolean ordinaryOnly) {
        return movements.stream()
            .filter(movement -> movement.getDirection() == direction)
            .filter(movement -> !ordinaryOnly || movement.getNature() == FinancialMovementNature.ORDINARY)
            .map(FinancialMovement::getAmount)
            .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal signedAmount(FinancialMovement movement) {
        return movement.getDirection() == FinancialDirection.INCOME ? movement.getAmount() : movement.getAmount().negate();
    }

    private FinancialAccount requiredAccount(long id, boolean active) {
        FinancialAccount account = accountRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conto non trovato", "finance.account.notFound"));
        if (active && !account.isActive()) throw error(HttpStatus.CONFLICT, "Il conto è archiviato", "finance.account.archived");
        return account;
    }

    private FinancialCategory requiredCategory(long id, boolean active) {
        FinancialCategory category = categoryRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Categoria non trovata", "finance.category.notFound"));
        if (active && !category.isActive()) throw error(HttpStatus.CONFLICT, "La categoria è archiviata", "finance.category.archived");
        return category;
    }

    private FinancialMovement requiredMovement(long id) {
        return movementRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Movimento non trovato", "finance.movement.notFound"));
    }

    private FinancialMovementAttachment requiredAttachment(long id) {
        return attachmentRepository.findByIdAndDeletedFalseAndActiveTrue(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Allegato non trovato", "finance.attachment.notFound"));
    }

    private CalendarEvents requiredEvent(long id) {
        return eventRepository.findById(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Evento non trovato", "finance.event.notFound"));
    }

    private AccountDTO toAccountDto(FinancialAccount account, BigDecimal balance) {
        return new AccountDTO(
            account.getId(), account.getName(), account.getDescription(), account.getAccountType(), account.getCurrency(), account.getIban(),
            account.getBankName(), account.isActive(), account.getDisplayOrder(), balance, account.getEntityVersion()
        );
    }

    private CategoryDTO toCategoryDto(FinancialCategory category) {
        return new CategoryDTO(
            category.getId(), category.getName(), category.getDescription(), category.getDirection(), category.isActive(), category.isSystemDefined(),
            category.getDisplayOrder(), category.getEntityVersion()
        );
    }

    private MovementDTO toMovementDto(FinancialMovement movement) {
        return new MovementDTO(
            movement.getId(), movement.getAccountingYear().getYear(), movement.getAccount().getId(), movement.getAccount().getName(),
            movement.getCategory() == null ? null : movement.getCategory().getId(),
            movement.getCategory() == null ? null : movement.getCategory().getName(),
            movement.getEvent() == null ? null : movement.getEvent().getId(), movement.getEventNameSnapshot(), movement.getDirection(), movement.getNature(),
            movement.getBookingDate(), movement.getValueDate(), movement.getAmount(), movement.getCurrency(), movement.getDescription(), movement.getCounterparty(),
            movement.getDocumentReference(), movement.getNotes(), movement.getTransferGroup(), movement.isReconciled(), movement.getReconciledAt(),
            movement.getReconciliationReference(), movement.getEntityVersion()
        );
    }

    private AttachmentDTO toAttachmentDto(FinancialMovementAttachment attachment) {
        var media = attachment.getMediaAsset();
        return new AttachmentDTO(attachment.getId(), attachment.getMovement().getId(), media.getId(), media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), attachment.getDescription());
    }

    private YearDTO toYearDto(AccountingYear year) {
        return new YearDTO(year.getYear(), year.getStartDate(), year.getEndDate(), year.getStatus(), year.getRolledOverAt(), year.getLastRecalculatedAt());
    }

    private static BigDecimal moneyOrZero(BigDecimal value) { return value == null ? ZERO : value; }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    private static String trimToNull(String value) { return notBlank(value) ? value.trim() : null; }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (!notBlank(value)) throw error(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "finance.identity.missing");
        return value;
    }

    private static String tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (!notBlank(value)) throw error(HttpStatus.BAD_REQUEST, "Tenant non disponibile", "finance.tenant.missing");
        return value;
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }

    private record RolloverResult(YearDTO year, boolean changed) {}
}
