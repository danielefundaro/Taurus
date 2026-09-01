package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.FinancialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long> {
    List<FinancialAccount> findAllByDeletedFalseOrderByDisplayOrderAscNameAsc();
    List<FinancialAccount> findAllByDeletedFalseAndActiveTrueOrderByDisplayOrderAscNameAsc();
    Optional<FinancialAccount> findByIdAndDeletedFalse(Long id);
    boolean existsByNameIgnoreCaseAndDeletedFalseAndActiveTrue(String name);
    boolean existsByNameIgnoreCaseAndIdNotAndDeletedFalseAndActiveTrue(String name, Long id);
}
