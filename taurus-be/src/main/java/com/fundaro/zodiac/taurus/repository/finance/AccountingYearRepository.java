package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.AccountingYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountingYearRepository extends JpaRepository<AccountingYear, Long> {
    Optional<AccountingYear> findByYearAndDeletedFalse(int year);

    List<AccountingYear> findAllByDeletedFalseOrderByYearAsc();
}
