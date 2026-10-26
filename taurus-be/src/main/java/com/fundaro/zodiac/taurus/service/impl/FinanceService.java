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
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountYearBalanceDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AttachmentDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryTotalDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.DashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventBudgetRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventCostDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventCostRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventEconomicLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.ReconciliationRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.ReorderRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.StatementLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearAnomalyDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import com.fundaro.zodiac.taurus.service.finance.FinanceMetrics;
import com.fundaro.zodiac.taurus.service.finance.FinanceMetrics.Entity;
import com.fundaro.zodiac.taurus.service.finance.FinanceMetrics.FailureReason;
import com.fundaro.zodiac.taurus.service.finance.FinanceMetrics.Operation;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.criteria.Predicate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    private static final Logger LOG = LoggerFactory.getLogger(FinanceService.class);

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
    private final FinanceMetrics metrics;

    public FinanceService(
        AccountingYearRepository yearRepository,
        FinancialAccountRepository accountRepository,
        FinancialCategoryRepository categoryRepository,
        FinancialMovementRepository movementRepository,
        FinancialMovementAttachmentRepository attachmentRepository,
        CalendarEventsRepository eventRepository,
        MediaRepository mediaRepository,
        MediaService mediaService,
        FinanceMetrics metrics
    ) {
        this.yearRepository = yearRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.attachmentRepository = attachmentRepository;
        this.eventRepository = eventRepository;
        this.mediaRepository = mediaRepository;
        this.mediaService = mediaService;
        this.metrics = metrics;
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
            opening.setInitialBalance(true);
            opening.setBookingDate(openingDate);
            opening.setAmount(request.initialBalance().abs());
            opening.setCurrency(account.getCurrency());
            // La descrizione scelta dall'utente spiega da dove viene il saldo; senza di essa resta
            // l'etichetta tecnica, che la migrazione usa per riconoscere le aperture esistenti.
            opening.setDescription(Objects.requireNonNullElse(trimToNull(request.initialBalanceDescription()), "Saldo iniziale"));
            movementRepository.save(opening);
            recalculateFollowingYears(openingDate.getYear(), actor(token));
        }
        metrics.record(Entity.ACCOUNT, Operation.CREATED);
        // L'IBAN non entra mai per intero in un log: ne basta la coda per riconoscere il conto.
        LOG.info("finance: creato il conto {} ({}) con iban {}", account.getId(), account.getAccountType(), maskedIban(account.getIban()));
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
        metrics.record(Entity.ACCOUNT, Operation.UPDATED);
        LOG.info("finance: aggiornato il conto {} con iban {}", id, maskedIban(account.getIban()));
        return toAccountDto(account, balance(id, LocalDate.now()));
    }

    public void archiveAccount(long id, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        account.setActive(false);
        account.touchAudit(actor(token));
        accountRepository.save(account);
        metrics.record(Entity.ACCOUNT, Operation.ARCHIVED);
    }

    /**
     * L'ordine dei conti scelto dall'utente. Gli identificativi assenti dall'elenco conservano la
     * posizione che avevano: l'interfaccia riordina quello che mostra, non tutto l'archivio.
     */
    public List<AccountDTO> reorderAccounts(ReorderRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        List<Long> ids = request.ids();
        for (int position = 0; position < ids.size(); position++) {
            FinancialAccount account = requiredAccount(ids.get(position), false);
            account.setDisplayOrder(position);
            account.touchAudit(actor);
            accountRepository.save(account);
        }
        metrics.record(Entity.ACCOUNT, Operation.REORDERED);
        return findAccounts(true, token);
    }

    /** L'ordine delle categorie scelto dall'utente, con la stessa regola dei conti. */
    public List<CategoryDTO> reorderCategories(ReorderRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        List<Long> ids = request.ids();
        for (int position = 0; position < ids.size(); position++) {
            FinancialCategory category = requiredCategory(ids.get(position), false);
            category.setDisplayOrder(position);
            category.touchAudit(actor);
            categoryRepository.save(category);
        }
        metrics.record(Entity.CATEGORY, Operation.REORDERED);
        return findCategories(true, token);
    }

    public AccountDTO restoreAccount(long id, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        if (account.isActive()) {
            throw error(HttpStatus.CONFLICT, "Il conto è già attivo", "finance.account.alreadyActive");
        }
        if (accountRepository.existsByNameIgnoreCaseAndIdNotAndDeletedFalseAndActiveTrue(account.getName(), id)) {
            throw error(HttpStatus.CONFLICT, "Esiste già un conto attivo con questo nome", "finance.account.nameExists");
        }
        account.setActive(true);
        account.touchAudit(actor(token));
        accountRepository.save(account);
        metrics.record(Entity.ACCOUNT, Operation.RESTORED);
        return toAccountDto(account, balance(id, LocalDate.now()));
    }

    public void deleteAccount(long id, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(id, false);
        if (account.isActive()) {
            throw error(HttpStatus.CONFLICT, "Il conto deve essere archiviato prima di essere eliminato", "finance.account.notArchived");
        }
        if (movementCount(id) > 0) {
            throw error(
                HttpStatus.CONFLICT,
                "Il conto ha movimenti attivi: può essere soltanto archiviato",
                "finance.account.hasMovements"
            );
        }
        account.setDeleted(true);
        account.touchAudit(actor(token));
        accountRepository.save(account);
        metrics.record(Entity.ACCOUNT, Operation.DELETED);
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
        Integer year,
        Long accountId,
        Long categoryId,
        Long eventId,
        FinancialDirection direction,
        FinancialMovementNature nature,
        String counterparty,
        Boolean reconciled,
        String query,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        Specification<FinancialMovement> specification = movementSpecification(
            from,
            to,
            year,
            accountId,
            categoryId,
            eventId,
            direction,
            nature,
            counterparty,
            reconciled,
            query
        );
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
            if (existing != null) {
                metrics.record(Entity.MOVEMENT, Operation.REPLAYED);
                return existing;
            }
        }
        FinancialMovement movement = new FinancialMovement();
        movement.initializeAudit(actor);
        movement.setNature(FinancialMovementNature.ORDINARY);
        applyMovement(movement, request, actor);
        movementRepository.save(movement);
        recalculateFollowingYears(movement.getAccountingYear().getYear(), actor);
        metrics.record(Entity.MOVEMENT, Operation.CREATED);
        LOG.debug("finance: registrato il movimento {} sull'esercizio {}", movement.getId(), movement.getAccountingYear().getYear());
        return toMovementDto(movement);
    }

    public MovementDTO updateMovement(long id, MovementRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        FinancialMovement movement = requiredMovement(id);
        requireCurrentVersion(movement, request.version());
        int oldYear = movement.getAccountingYear().getYear();
        if (movement.getNature() == FinancialMovementNature.TRANSFER) {
            updateTransferPair(movement, request, actor);
        } else {
            applyMovement(movement, request, actor);
            movement.touchAudit(actor);
            movementRepository.save(movement);
        }
        recalculateFollowingYears(Math.min(oldYear, movement.getAccountingYear().getYear()), actor);
        metrics.record(Entity.MOVEMENT, Operation.UPDATED);
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
        metrics.record(movement.getNature() == FinancialMovementNature.TRANSFER ? Entity.TRANSFER : Entity.MOVEMENT, Operation.DELETED);
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
        metrics.record(Entity.MOVEMENT, Operation.RECONCILED);
        return toMovementDto(movement);
    }

    public TransferDTO createTransfer(TransferRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        // Un retry HTTP non deve spostare il denaro due volte: con la stessa chiave si restituisce
        // il trasferimento già creato, ricostruito dalle due scritture del suo gruppo.
        if (request.requestKey() != null) {
            TransferDTO replay = movementRepository.findByRequestKeyAndDeletedFalse(request.requestKey())
                .map(FinancialMovement::getTransferGroup)
                .map(this::transferByGroup)
                .orElse(null);
            if (replay != null) {
                metrics.record(Entity.TRANSFER, Operation.REPLAYED);
                return replay;
            }
        }
        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw rejected(
                FailureReason.SAME_ACCOUNT_TRANSFER,
                HttpStatus.BAD_REQUEST,
                "Il conto di origine e quello di destinazione devono essere diversi",
                "finance.transfer.sameAccount"
            );
        }
        FinancialAccount source = requiredAccount(request.sourceAccountId(), true);
        FinancialAccount destination = requiredAccount(request.destinationAccountId(), true);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw rejected(
                FailureReason.CURRENCY_MISMATCH,
                HttpStatus.BAD_REQUEST,
                "I conti del trasferimento devono avere la stessa valuta",
                "finance.transfer.currencyMismatch"
            );
        }
        UUID group = UUID.randomUUID();
        AccountingYear year = ensureYear(request.bookingDate().getYear(), actor);
        FinancialMovement outgoing = transferLeg(source, year, FinancialDirection.EXPENSE, request, group, actor);
        FinancialMovement incoming = transferLeg(destination, year, FinancialDirection.INCOME, request, group, actor);
        // La chiave sta sulla sola uscita: identifica il trasferimento, non la singola scrittura.
        outgoing.setRequestKey(request.requestKey());
        movementRepository.saveAll(List.of(outgoing, incoming));
        recalculateFollowingYears(year.getYear(), actor);
        metrics.record(Entity.TRANSFER, Operation.CREATED);
        LOG.info("finance: trasferimento {} registrato sull'esercizio {}", group, year.getYear());
        return new TransferDTO(group, toMovementDto(outgoing), toMovementDto(incoming));
    }

    /** Le due scritture di un gruppo lette come trasferimento; null se il gruppo non è completo. */
    private TransferDTO transferByGroup(UUID group) {
        if (group == null) return null;
        List<FinancialMovement> legs = movementRepository.findAllByTransferGroupAndDeletedFalse(group);
        FinancialMovement outgoing = legs.stream().filter(leg -> leg.getDirection() == FinancialDirection.EXPENSE).findFirst().orElse(null);
        FinancialMovement incoming = legs.stream().filter(leg -> leg.getDirection() == FinancialDirection.INCOME).findFirst().orElse(null);
        if (outgoing == null || incoming == null) return null;
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
        // Le posizioni evento aperte sono il lavoro economico che resta: quanto c'è da incassare e
        // quanto da pagare, letto dalle righe del periodo scelto.
        List<EventEconomicLineDTO> openEvents = eventLines(effectiveFrom, effectiveTo, movements).stream()
            .filter(FinanceService::economicallyOpen)
            .toList();
        BigDecimal toCollect = openEvents.stream().map(EventEconomicLineDTO::remainingIncome).filter(value -> value.signum() > 0).reduce(ZERO, BigDecimal::add);
        BigDecimal toPay = openEvents.stream().map(EventEconomicLineDTO::remainingExpense).filter(value -> value.signum() > 0).reduce(ZERO, BigDecimal::add);
        YearDTO lastRollover = yearRepository.findAllByDeletedFalseOrderByYearAsc().stream()
            .filter(year -> year.getRolledOverAt() != null)
            .reduce((first, second) -> second)
            .map(this::toYearDto)
            .orElse(null);
        return new DashboardDTO(
            effectiveFrom,
            effectiveTo,
            totalBalance,
            income,
            expense,
            income.subtract(expense),
            movements.size(),
            unreconciled,
            toCollect,
            toPay,
            openEvents,
            lastRollover,
            accounts
        );
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
            throw rejected(FailureReason.ATTACHMENT_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE, "Ogni allegato deve avere dimensione massima di 10 MB", "finance.attachment.tooLarge").with("maxMegabytes", (int) (MAX_ATTACHMENT_SIZE / (1024 * 1024)));
        }
        if (attachmentRepository.countByMovement_IdAndDeletedFalseAndActiveTrue(movementId) >= MAX_ATTACHMENTS) {
            throw rejected(FailureReason.TOO_MANY_ATTACHMENTS, HttpStatus.CONFLICT, "Sono consentiti al massimo 20 allegati per movimento", "finance.attachment.limit").with("maximum", MAX_ATTACHMENTS);
        }
        String mime = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase(Locale.ROOT);
        if (!List.of("application/pdf", "image/jpeg", "image/png").contains(mime)) {
            throw rejected(FailureReason.UNSUPPORTED_TYPE, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Sono supportati soltanto PDF, JPEG e PNG", "finance.attachment.unsupported");
        }
        FinancialMovement movement = requiredMovement(movementId);
        byte[] bytes = file.getBytes();
        // Il MIME dichiarato dal client è un'affermazione, non una prova: il tipo si riconosce dai
        // primi byte del file, così un eseguibile rinominato .pdf non entra nello storage del tenant.
        if (!mime.equals(sniffedMime(bytes))) {
            LOG.warn("finance: allegato rifiutato sul movimento {}, il contenuto non corrisponde al tipo {}", movementId, mime);
            throw rejected(
                FailureReason.CONTENT_MISMATCH,
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Il contenuto del file non corrisponde al tipo dichiarato",
                "finance.attachment.contentMismatch"
            );
        }
        MediaDTO stored = mediaService.store(bytes, file.getOriginalFilename(), mime, "finance", token);
        FinancialMovementAttachment attachment = new FinancialMovementAttachment();
        attachment.initializeAudit(actor(token));
        attachment.setMovement(movement);
        attachment.setMediaAsset(mediaRepository.getReferenceById(stored.getId()));
        attachment.setDescription(trimToNull(description));
        attachmentRepository.save(attachment);
        metrics.record(Entity.ATTACHMENT, Operation.CREATED);
        metrics.recordAttachmentSize(bytes.length);
        return toAttachmentDto(attachment);
    }

    /**
     * Il tipo riconosciuto dai byte iniziali, oppure {@code null} se non è uno dei tre ammessi.
     * I tre formati hanno firme corte e stabili: non serve una libreria per distinguerli.
     */
    static String sniffedMime(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return null;
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) return "application/pdf";
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) return false;
        }
        return true;
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
        metrics.record(Entity.ATTACHMENT, Operation.DOWNLOADED);
        return mediaService.getContent(attachment.getMediaAsset().getId(), token);
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
        metrics.record(Entity.ATTACHMENT, Operation.DELETED);
    }

    @Transactional(readOnly = true)
    public EventSummaryDTO eventSummary(long eventId, AbstractAuthenticationToken token) {
        tenant(token);
        return eventSummary(requiredEvent(eventId));
    }

    /**
     * La scheda Eventi con i suoi filtri. Lo stato economico è calcolato dai movimenti, non è una
     * colonna: periodo, testo e stato si applicano quindi in memoria sull'elenco degli eventi, che
     * per un'associazione resta dell'ordine delle centinaia.
     */
    @Transactional(readOnly = true)
    public Page<EventSummaryDTO> findEvents(
        LocalDate from,
        LocalDate to,
        String status,
        String queryText,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        tenant(token);
        if (from != null && to != null) requirePeriod(from, to);
        String pattern = notBlank(queryText) ? queryText.trim().toLowerCase(Locale.ROOT) : null;
        List<EventSummaryDTO> summaries = eventRepository.findAll().stream()
            .filter(event -> !Boolean.TRUE.equals(event.getDeleted()))
            .filter(event -> from == null || (eventDate(event) != null && !eventDate(event).isBefore(from)))
            .filter(event -> to == null || (eventDate(event) != null && !eventDate(event).isAfter(to)))
            .sorted(Comparator.comparing(FinanceService::eventDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(this::eventSummary)
            .filter(summary -> !notBlank(status) || status.trim().equalsIgnoreCase(summary.economicStatus()))
            .filter(summary -> pattern == null || (summary.eventName() != null && summary.eventName().toLowerCase(Locale.ROOT).contains(pattern)))
            .toList();
        int start = (int) Math.min(pageable.getOffset(), summaries.size());
        int end = pageable.isPaged() ? Math.min(start + pageable.getPageSize(), summaries.size()) : summaries.size();
        return new PageImpl<>(summaries.subList(start, end), pageable, summaries.size());
    }

    /**
     * Il rendiconto di un evento: il preventivo, i movimenti registrati e lo scostamento fra i due.
     * È quello che serve per spiegare a un consiglio direttivo come è andata una manifestazione.
     */
    @Transactional(readOnly = true)
    public EventStatementDTO eventStatement(long eventId, AbstractAuthenticationToken token) {
        tenant(token);
        EventSummaryDTO summary = eventSummary(requiredEvent(eventId));
        return new EventStatementDTO(
            summary,
            summary.received().subtract(summary.expectedFee()),
            summary.paid().subtract(summary.expectedCosts()),
            summary.actualResult().subtract(summary.expectedMargin())
        );
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
        // Una modifica alla singola occorrenza protegge l'intera occorrenza dagli aggiornamenti del
        // template della serie: compenso e costi non sono un'eccezione a quella decisione.
        if (event.getSeries() != null) event.setSeriesException(true);
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

    /**
     * Genera il riporto dell'esercizio. Su un esercizio già riportato il comando resta efficace e
     * rifà le aperture dell'anno successivo: dopo una correzione tardiva è l'unico modo di
     * riallineare i saldi, e rifiutarlo lasciava il riporto vecchio senza dirlo a nessuno. La
     * segnalazione automatica, invece, scatta soltanto la prima volta: {@code changed} dice questo.
     */
    private RolloverResult performRollover(int year, String actor) {
        AccountingYear source = ensureYear(year, actor);
        boolean first = source.getStatus() != AccountingYearStatus.ROLLED_OVER;
        ensureYear(year + 1, actor);
        recalculateOpening(year + 1, actor);
        source.setStatus(AccountingYearStatus.ROLLED_OVER);
        source.setRolledOverAt(ZonedDateTime.now());
        source.setRolledOverBy(actor);
        source.touchAudit(actor);
        yearRepository.save(source);
        metrics.record(Entity.YEAR, Operation.ROLLED_OVER);
        LOG.info("finance: riporto dell'esercizio {} {}", year, first ? "generato" : "rigenerato");
        return new RolloverResult(toYearDto(source), first);
    }

    public YearDTO recalculate(int year, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        AccountingYear source = ensureYear(year, actor);
        recalculateFollowingYears(year, actor);
        source.setLastRecalculatedAt(ZonedDateTime.now());
        source.touchAudit(actor);
        metrics.record(Entity.YEAR, Operation.RECALCULATED);
        return toYearDto(yearRepository.save(source));
    }

    @Transactional(readOnly = true)
    public AccountStatementDTO accountStatement(long accountId, LocalDate from, LocalDate to, AbstractAuthenticationToken token) {
        tenant(token);
        FinancialAccount account = requiredAccount(accountId, false);
        LocalDate effectiveTo = Objects.requireNonNullElse(to, LocalDate.now());
        LocalDate effectiveFrom = Objects.requireNonNullElse(from, effectiveTo.withDayOfYear(1));
        requirePeriod(effectiveFrom, effectiveTo);
        List<FinancialMovement> accountMovements = periodMovements(effectiveFrom, effectiveTo).stream()
            .filter(movement -> movement.getAccount().getId().equals(accountId))
            .toList();
        BigDecimal opening = balance(accountId, effectiveFrom.minusDays(1)).add(initialBalance(accountMovements));
        List<FinancialMovement> movements = accountMovements.stream()
            .filter(movement -> movement.getNature() != FinancialMovementNature.OPENING)
            .toList();
        List<StatementLineDTO> lines = new ArrayList<>();
        BigDecimal running = opening;
        for (FinancialMovement movement : movements) {
            running = running.add(signedAmount(movement));
            lines.add(new StatementLineDTO(toMovementDto(movement), running));
        }
        return new AccountStatementDTO(
            toAccountDto(account, balance(accountId, effectiveTo)),
            effectiveFrom,
            effectiveTo,
            opening,
            total(movements, FinancialDirection.INCOME, false),
            total(movements, FinancialDirection.EXPENSE, false),
            running,
            lines
        );
    }

    @Transactional(readOnly = true)
    public YearDTO findYear(int year, AbstractAuthenticationToken token) {
        tenant(token);
        return toYearDto(requiredYear(year));
    }

    @Transactional(readOnly = true)
    public YearSummaryDTO yearSummary(int year, AbstractAuthenticationToken token) {
        tenant(token);
        AccountingYear entity = requiredYear(year);
        LocalDate from = Objects.requireNonNullElse(entity.getStartDate(), LocalDate.of(year, 1, 1));
        LocalDate to = Objects.requireNonNullElse(entity.getEndDate(), LocalDate.of(year, 12, 31));
        List<FinancialMovement> movements = periodMovements(from, to);

        List<AccountYearBalanceDTO> accounts = new ArrayList<>();
        BigDecimal openingTotal = ZERO;
        BigDecimal closingTotal = ZERO;
        for (FinancialAccount account : accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()) {
            List<FinancialMovement> accountMovements = movements.stream()
                .filter(movement -> movement.getAccount().getId().equals(account.getId()))
                .toList();
            List<FinancialMovement> own = accountMovements.stream()
                .filter(movement -> movement.getNature() != FinancialMovementNature.OPENING)
                .toList();
            BigDecimal opening = balance(account.getId(), from.minusDays(1)).add(initialBalance(accountMovements));
            BigDecimal income = total(own, FinancialDirection.INCOME, false);
            BigDecimal expense = total(own, FinancialDirection.EXPENSE, false);
            BigDecimal closing = opening.add(income).subtract(expense);
            accounts.add(new AccountYearBalanceDTO(account.getId(), account.getName(), opening, income, expense, closing));
            openingTotal = openingTotal.add(opening);
            closingTotal = closingTotal.add(closing);
        }

        BigDecimal ordinaryIncome = total(movements, FinancialDirection.INCOME, true);
        BigDecimal ordinaryExpense = total(movements, FinancialDirection.EXPENSE, true);
        BigDecimal transferTotal = movements.stream()
            .filter(movement -> movement.getNature() == FinancialMovementNature.TRANSFER)
            .filter(movement -> movement.getDirection() == FinancialDirection.EXPENSE)
            .map(FinancialMovement::getAmount)
            .reduce(ZERO, BigDecimal::add);
        List<FinancialMovement> unreconciled = movements.stream()
            .filter(movement -> movement.getNature() == FinancialMovementNature.ORDINARY && !movement.isReconciled())
            .toList();
        List<YearAnomalyDTO> anomalies = anomalies(movements, accounts);
        anomalies.forEach(anomaly -> metrics.recordAnomaly(anomaly.code(), 1));

        return new YearSummaryDTO(
            toYearDto(entity),
            accounts,
            openingTotal,
            ordinaryIncome,
            ordinaryExpense,
            ordinaryIncome.subtract(ordinaryExpense),
            transferTotal,
            closingTotal,
            categoryTotals(movements),
            eventLines(from, to, movements).stream().filter(FinanceService::economicallyOpen).toList(),
            unreconciled.size(),
            unreconciled.stream().map(FinancialMovement::getAmount).reduce(ZERO, BigDecimal::add),
            anomalies,
            entity.getLastRecalculatedAt()
        );
    }

    /**
     * Le segnalazioni dell'esercizio. Sono informative per scelta: la contabilità di un'associazione
     * ha casi irregolari legittimi, e bloccarli costringerebbe a scritture finte. Chi legge decide.
     */
    private List<YearAnomalyDTO> anomalies(List<FinancialMovement> movements, List<AccountYearBalanceDTO> accounts) {
        List<YearAnomalyDTO> anomalies = new ArrayList<>();
        Map<UUID, List<FinancialMovement>> groups = new LinkedHashMap<>();
        movements.stream()
            .filter(movement -> movement.getNature() == FinancialMovementNature.TRANSFER && movement.getTransferGroup() != null)
            .forEach(movement -> groups.computeIfAbsent(movement.getTransferGroup(), key -> new ArrayList<>()).add(movement));
        groups.forEach((group, legs) -> {
            // Una gamba sola può essere legittima a cavallo di due esercizi: la coppia è nello stesso
            // anno per costruzione, quindi qui una gamba spaiata è davvero un'anomalia.
            if (legs.size() != 2) {
                anomalies.add(new YearAnomalyDTO(
                    "TRANSFER_INCOMPLETE",
                    "Il trasferimento ha " + legs.size() + " scritture invece di due",
                    legs.get(0).getId(),
                    group
                ));
                return;
            }
            FinancialMovement first = legs.get(0);
            FinancialMovement second = legs.get(1);
            if (first.getAmount().compareTo(second.getAmount()) != 0) {
                anomalies.add(new YearAnomalyDTO("TRANSFER_AMOUNT_MISMATCH", "Le due scritture del trasferimento hanno importi diversi", first.getId(), group));
            }
            if (!Objects.equals(first.getBookingDate(), second.getBookingDate())) {
                anomalies.add(new YearAnomalyDTO("TRANSFER_DATE_MISMATCH", "Le due scritture del trasferimento hanno date contabili diverse", first.getId(), group));
            }
            if (!Objects.equals(first.getCurrency(), second.getCurrency())) {
                anomalies.add(new YearAnomalyDTO("TRANSFER_CURRENCY_MISMATCH", "Le due scritture del trasferimento hanno valute diverse", first.getId(), group));
            }
            if (first.getDirection() == second.getDirection()) {
                anomalies.add(new YearAnomalyDTO("TRANSFER_DIRECTION_MISMATCH", "Le due scritture del trasferimento hanno la stessa direzione", first.getId(), group));
            }
            if (Objects.equals(first.getAccount().getId(), second.getAccount().getId())) {
                anomalies.add(new YearAnomalyDTO("TRANSFER_SAME_ACCOUNT", "Il trasferimento ha origine e destinazione sullo stesso conto", first.getId(), group));
            }
        });
        accounts.stream()
            .filter(account -> account.closingBalance().signum() < 0)
            .forEach(account -> anomalies.add(new YearAnomalyDTO(
                "ACCOUNT_NEGATIVE_CLOSING",
                "Il conto " + account.accountName() + " chiude l'esercizio con saldo negativo",
                null,
                null
            )));
        long missingAttachments = movements.stream()
            .filter(movement -> movement.getNature() == FinancialMovementNature.ORDINARY)
            .filter(movement -> attachmentRepository.countByMovement_IdAndDeletedFalseAndActiveTrue(movement.getId()) == 0)
            .count();
        if (missingAttachments > 0) {
            anomalies.add(new YearAnomalyDTO(
                "MOVEMENTS_WITHOUT_ATTACHMENT",
                missingAttachments + " movimenti ordinari non hanno un documento allegato",
                null,
                null
            ));
        }
        return List.copyOf(anomalies);
    }

    @Transactional(readOnly = true)
    public List<CategoryTotalDTO> categoryTotals(LocalDate from, LocalDate to, AbstractAuthenticationToken token) {
        tenant(token);
        LocalDate effectiveTo = Objects.requireNonNullElse(to, LocalDate.now());
        LocalDate effectiveFrom = Objects.requireNonNullElse(from, effectiveTo.withDayOfYear(1));
        requirePeriod(effectiveFrom, effectiveTo);
        return categoryTotals(periodMovements(effectiveFrom, effectiveTo));
    }

    @Transactional(readOnly = true)
    public List<EventEconomicLineDTO> eventLines(LocalDate from, LocalDate to, AbstractAuthenticationToken token) {
        tenant(token);
        LocalDate effectiveTo = Objects.requireNonNullElse(to, LocalDate.now());
        LocalDate effectiveFrom = Objects.requireNonNullElse(from, effectiveTo.withDayOfYear(1));
        requirePeriod(effectiveFrom, effectiveTo);
        return eventLines(effectiveFrom, effectiveTo, periodMovements(effectiveFrom, effectiveTo));
    }

    /**
     * Eventi rilevanti per il periodo: quelli che vi si svolgono e quelli che vi
     * hanno movimenti, cosi un incasso registrato l'anno successivo resta visibile
     * nel rendiconto in cui e' stato contabilizzato.
     */
    private List<EventEconomicLineDTO> eventLines(LocalDate from, LocalDate to, List<FinancialMovement> movements) {
        Set<Long> withMovements = movements.stream()
            .filter(movement -> movement.getEvent() != null)
            .map(movement -> movement.getEvent().getId())
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return eventRepository.findAll().stream()
            .filter(event -> withMovements.contains(event.getId()) || withinPeriod(eventDate(event), from, to))
            .map(this::eventLine)
            .sorted(Comparator.comparing(EventEconomicLineDTO::eventDate, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private EventEconomicLineDTO eventLine(CalendarEvents event) {
        EventSummaryDTO summary = eventSummary(event);
        return new EventEconomicLineDTO(
            summary.eventId(),
            summary.eventName(),
            eventDate(event),
            summary.expectedFee(),
            summary.expectedCosts(),
            summary.expectedMargin(),
            summary.received(),
            summary.paid(),
            summary.actualResult(),
            summary.remainingIncome(),
            summary.remainingExpense(),
            summary.economicStatus()
        );
    }

    private List<CategoryTotalDTO> categoryTotals(List<FinancialMovement> movements) {
        Map<Long, CategoryAccumulator> accumulators = new LinkedHashMap<>();
        for (FinancialMovement movement : movements) {
            if (movement.getNature() != FinancialMovementNature.ORDINARY) continue;
            FinancialCategory category = movement.getCategory();
            Long key = category == null ? UNCATEGORIZED : category.getId();
            CategoryAccumulator accumulator = accumulators.computeIfAbsent(
                key,
                ignored ->
                    new CategoryAccumulator(
                        category == null ? null : category.getId(),
                        category == null ? "Senza categoria" : category.getName(),
                        category == null ? null : category.getDirection()
                    )
            );
            accumulator.add(movement.getDirection(), movement.getAmount());
        }
        return accumulators
            .values()
            .stream()
            .sorted(Comparator.comparing(accumulator -> accumulator.name.toLowerCase(Locale.ROOT)))
            .map(CategoryAccumulator::toDto)
            .toList();
    }

    private List<FinancialMovement> periodMovements(LocalDate from, LocalDate to) {
        return movementRepository
            .findAllByDeletedFalseAndBookingDateBetween(from, to)
            .stream()
            .sorted((left, right) -> {
                int date = left.getBookingDate().compareTo(right.getBookingDate());
                return date != 0 ? date : left.getId().compareTo(right.getId());
            })
            .toList();
    }

    private AccountingYear requiredYear(int year) {
        return yearRepository
            .findByYearAndDeletedFalse(year)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Esercizio non trovato", "finance.year.notFound"));
    }

    private void requirePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw error(HttpStatus.BAD_REQUEST, "La data iniziale deve precedere la data finale", "finance.report.invalidPeriod");
        }
    }

    private static boolean economicallyOpen(EventEconomicLineDTO line) {
        return !"NO_BUDGET".equals(line.economicStatus()) && !"SETTLED".equals(line.economicStatus());
    }

    private static boolean withinPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    private static LocalDate eventDate(CalendarEvents event) {
        return event.getStartDate() == null
            ? null
            : Instant.ofEpochMilli(event.getStartDate().getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static final Long UNCATEGORIZED = -1L;

    private static final class CategoryAccumulator {

        private final Long id;
        private final String name;
        private final FinancialCategoryDirection direction;
        private BigDecimal income = ZERO;
        private BigDecimal expense = ZERO;
        private long count;

        private CategoryAccumulator(Long id, String name, FinancialCategoryDirection direction) {
            this.id = id;
            this.name = name;
            this.direction = direction;
        }

        private void add(FinancialDirection movementDirection, BigDecimal amount) {
            if (movementDirection == FinancialDirection.INCOME) income = income.add(amount);
            else expense = expense.add(amount);
            count++;
        }

        private CategoryTotalDTO toDto() {
            return new CategoryTotalDTO(id, name, direction, income, expense, income.subtract(expense), count);
        }
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
        if (pair.size() != 2)
            throw error(HttpStatus.CONFLICT, "Il trasferimento non contiene due movimenti coerenti", "finance.transfer.inconsistent");
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
            // The account starts in this year: its opening is the balance entered by hand, not a carry-over.
            if (opening != null && opening.isInitialBalance()) continue;
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

    Specification<FinancialMovement> movementSpecification(
        LocalDate from,
        LocalDate to,
        Integer year,
        Long accountId,
        Long categoryId,
        Long eventId,
        FinancialDirection direction,
        FinancialMovementNature nature,
        String counterparty,
        Boolean reconciled,
        String queryText
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isFalse(root.get("deleted")));
            if (from != null) predicates.add(builder.greaterThanOrEqualTo(root.get("bookingDate"), from));
            if (to != null) predicates.add(builder.lessThanOrEqualTo(root.get("bookingDate"), to));
            // L'esercizio è il filtro con cui si lavora in contabilità: non coincide con l'intervallo
            // di date, perché un movimento può essere registrato in un esercizio e pagato in un altro.
            if (year != null) predicates.add(builder.equal(root.get("accountingYear").get("year"), year));
            if (nature != null) predicates.add(builder.equal(root.get("nature"), nature));
            if (notBlank(counterparty)) {
                predicates.add(builder.like(builder.lower(root.get("counterparty")), "%" + counterparty.trim().toLowerCase(Locale.ROOT) + "%"));
            }
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

    /** Starting balances of the accounts opened in the period: the carry-overs are already in the previous balance. */
    private BigDecimal initialBalance(List<FinancialMovement> movements) {
        return movements.stream()
            .filter(movement -> movement.getNature() == FinancialMovementNature.OPENING && movement.isInitialBalance())
            .map(this::signedAmount)
            .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal signedAmount(FinancialMovement movement) {
        return movement.getDirection() == FinancialDirection.INCOME ? movement.getAmount() : movement.getAmount().negate();
    }

    private FinancialAccount requiredAccount(long id, boolean active) {
        FinancialAccount account = accountRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Conto non trovato", "finance.account.notFound"));
        if (active && !account.isActive())
            throw error(HttpStatus.CONFLICT, "Il conto è archiviato", "finance.account.archived");
        return account;
    }

    private FinancialCategory requiredCategory(long id, boolean active) {
        FinancialCategory category = categoryRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Categoria non trovata", "finance.category.notFound"));
        if (active && !category.isActive())
            throw error(HttpStatus.CONFLICT, "La categoria è archiviata", "finance.category.archived");
        return category;
    }

    /**
     * Il controllo di concorrenza ottimistica chiesto dal client: chi invia la versione che ha letto
     * ottiene un rifiuto invece di sovrascrivere la correzione di un collega. Chi non la invia
     * conserva il comportamento di prima, perché la versione resta facoltativa.
     */
    private void requireCurrentVersion(FinancialMovement movement, Long expected) {
        if (expected == null || expected == movement.getEntityVersion()) return;
        throw rejected(
            FailureReason.VERSION_CONFLICT,
            HttpStatus.CONFLICT,
            "Il movimento è stato modificato da un altro utente: ricaricalo prima di salvare",
            "finance.movement.versionConflict"
        );
    }

    private FinancialMovement requiredMovement(long id) {
        return movementRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Movimento non trovato", "finance.movement.notFound"));
    }

    /**
     * L'allegato di un movimento eliminato non è più scaricabile: il documento seguiva quel
     * movimento, e un collegamento rimasto in giro non deve diventare una porta aperta.
     */
    private FinancialMovementAttachment requiredAttachment(long id) {
        FinancialMovementAttachment attachment = attachmentRepository.findByIdAndDeletedFalseAndActiveTrue(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Allegato non trovato", "finance.attachment.notFound"));
        FinancialMovement movement = attachment.getMovement();
        if (movement == null || movement.isDeleted()) {
            LOG.warn("finance: negato l'accesso all'allegato {} di un movimento eliminato", id);
            throw rejected(FailureReason.ACCESS_DENIED, HttpStatus.NOT_FOUND, "Allegato non trovato", "finance.attachment.notFound");
        }
        return attachment;
    }

    private CalendarEvents requiredEvent(long id) {
        return eventRepository.findById(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Evento non trovato", "finance.event.notFound"));
    }

    private AccountDTO toAccountDto(FinancialAccount account, BigDecimal balance) {
        return new AccountDTO(
            account.getId(), account.getName(), account.getDescription(), account.getAccountType(), account.getCurrency(), account.getIban(),
            account.getBankName(), account.isActive(), account.getDisplayOrder(), balance, movementCount(account.getId()),
            initialBalanceMovementId(account.getId()),
            account.getEntityVersion()
        );
    }

    /**
     * Il movimento che porta il saldo iniziale del conto: il frontend vi allega il documento che lo
     * giustifica, perché l'apertura è un movimento come gli altri e non un campo del conto.
     */
    private Long initialBalanceMovementId(Long accountId) {
        if (accountId == null) return null;
        return movementRepository.findAllByAccount_IdAndDeletedFalseAndInitialBalanceTrue(accountId).stream()
            .map(FinancialMovement::getId)
            .findFirst()
            .orElse(null);
    }

    /**
     * Conta le scritture ancora vive collegate al conto: le eliminate logicamente non ne impediscono l'eliminazione.
     */
    private long movementCount(Long accountId) {
        return accountId == null ? 0 : movementRepository.countByAccount_IdAndDeletedFalse(accountId);
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
            movement.getReconciliationReference(), attachmentCount(movement.getId()), movement.getEntityVersion()
        );
    }

    /**
     * Quanti documenti giustificano il movimento. Serve all'elenco per segnalare le righe senza
     * giustificativo: un movimento appena creato ne ha zero, e a quel punto l'indicazione e' utile.
     */
    private long attachmentCount(Long movementId) {
        return movementId == null ? 0 : attachmentRepository.countByMovement_IdAndDeletedFalseAndActiveTrue(movementId);
    }

    private AttachmentDTO toAttachmentDto(FinancialMovementAttachment attachment) {
        var media = attachment.getMediaAsset();
        return new AttachmentDTO(attachment.getId(), attachment.getMovement().getId(), media.getId(), media.getOriginalFilename(), media.getMimeType(), media.getFileSize(), attachment.getDescription());
    }

    private YearDTO toYearDto(AccountingYear year) {
        return new YearDTO(year.getYear(), year.getStartDate(), year.getEndDate(), year.getStatus(), year.getRolledOverAt(), year.getLastRecalculatedAt());
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (!notBlank(value))
            throw error(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "auth.identity.missing");
        return value;
    }

    private static String tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (!notBlank(value)) throw error(HttpStatus.FORBIDDEN, "Tenant non disponibile", "auth.tenant.missing");
        return value;
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }

    /** Lo stesso rifiuto di {@link #error}, contato anche come metrica del modulo. */
    private RequestAlertException rejected(FailureReason reason, HttpStatus status, String message, String key) {
        metrics.recordFailure(reason);
        return error(status, message, key);
    }

    /**
     * L'IBAN ridotto alle ultime quattro cifre. Serve a riconoscere il conto in un log senza
     * scriverci dentro una coordinata bancaria completa.
     */
    static String maskedIban(String iban) {
        if (!notBlank(iban)) return "n/d";
        String trimmed = iban.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
    }

    private record RolloverResult(YearDTO year, boolean changed) {
    }
}
