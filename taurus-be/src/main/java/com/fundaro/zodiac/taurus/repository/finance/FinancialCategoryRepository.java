package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialCategoryRepository extends JpaRepository<FinancialCategory, Long> {
    List<FinancialCategory> findAllByDeletedFalseOrderByDisplayOrderAscNameAsc();
    List<FinancialCategory> findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();
    Optional<FinancialCategory> findByIdAndDeletedFalse(Long id);
    boolean existsByNameIgnoreCaseAndDeletedFalseAndActiveTrue(String name);
    boolean existsByNameIgnoreCaseAndIdNotAndDeletedFalseAndActiveTrue(String name, Long id);
}
