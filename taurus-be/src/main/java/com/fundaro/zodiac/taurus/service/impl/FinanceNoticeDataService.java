package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.finance.AccountingYearRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialAccountRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementAttachmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Supplies domain snapshots to {@code NoticesAspect} without composing notification text. */
@Service
@Transactional(readOnly = true)
public class FinanceNoticeDataService {

    private final FinancialAccountRepository accountRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FinancialMovementRepository movementRepository;
    private final FinancialMovementAttachmentRepository attachmentRepository;
    private final AccountingYearRepository yearRepository;

    public FinanceNoticeDataService(
        FinancialAccountRepository accountRepository,
        FinancialCategoryRepository categoryRepository,
        FinancialMovementRepository movementRepository,
        FinancialMovementAttachmentRepository attachmentRepository,
        AccountingYearRepository yearRepository
    ) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.movementRepository = movementRepository;
        this.attachmentRepository = attachmentRepository;
        this.yearRepository = yearRepository;
    }

    public NamedNotice findAccount(long id) {
        return accountRepository.findByIdAndDeletedFalse(id)
            .map(account -> new NamedNotice(account.getId(), account.getName(), account.isActive()))
            .orElse(null);
    }

    public NamedNotice findCategory(long id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
            .map(category -> new NamedNotice(category.getId(), category.getName(), category.isActive()))
            .orElse(null);
    }

    public List<MovementNotice> findMovementGroup(long id) {
        FinancialMovement movement = movementRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (movement == null) return List.of();
        if (movement.getNature() != FinancialMovementNature.TRANSFER || movement.getTransferGroup() == null) {
            return List.of(toNotice(movement));
        }
        return movementRepository.findAllByTransferGroupAndDeletedFalse(movement.getTransferGroup()).stream()
            .map(FinanceNoticeDataService::toNotice)
            .toList();
    }

    public AttachmentNotice findAttachment(long id) {
        return attachmentRepository.findByIdAndDeletedFalseAndActiveTrue(id)
            .map(attachment -> new AttachmentNotice(
                attachment.getId(),
                attachment.getMediaAsset().getOriginalFilename(),
                toNotice(attachment.getMovement())
            ))
            .orElse(null);
    }

    public boolean movementExists(UUID requestKey) {
        return requestKey != null && movementRepository.findByRequestKeyAndDeletedFalse(requestKey).isPresent();
    }

    public AccountingYearStatus findYearStatus(int year) {
        return yearRepository.findByYearAndDeletedFalse(year).map(value -> value.getStatus()).orElse(null);
    }

    private static MovementNotice toNotice(FinancialMovement movement) {
        return new MovementNotice(
            movement.getId(),
            movement.getAccount().getName(),
            movement.getDirection(),
            movement.getNature(),
            movement.getBookingDate(),
            movement.getAmount(),
            movement.getCurrency(),
            movement.getDescription(),
            movement.getTransferGroup()
        );
    }

    public record NamedNotice(Long id, String name, boolean active) {}

    public record MovementNotice(
        Long id,
        String accountName,
        FinancialDirection direction,
        FinancialMovementNature nature,
        LocalDate bookingDate,
        BigDecimal amount,
        String currency,
        String description,
        UUID transferGroup
    ) {}

    public record AttachmentNotice(Long id, String fileName, MovementNotice movement) {}
}
