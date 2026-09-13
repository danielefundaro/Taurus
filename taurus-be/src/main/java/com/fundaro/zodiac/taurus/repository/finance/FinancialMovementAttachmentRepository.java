package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FinancialMovementAttachmentRepository extends JpaRepository<FinancialMovementAttachment, Long> {
    List<FinancialMovementAttachment> findAllByMovement_IdAndDeletedFalseAndActiveTrueOrderByInsertDateAsc(Long movementId);

    Optional<FinancialMovementAttachment> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByMovement_IdAndDeletedFalseAndActiveTrue(Long movementId);
}
