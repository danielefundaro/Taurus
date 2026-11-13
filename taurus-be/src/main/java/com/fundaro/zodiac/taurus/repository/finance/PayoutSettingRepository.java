package com.fundaro.zodiac.taurus.repository.finance;

import com.fundaro.zodiac.taurus.domain.finance.PayoutSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayoutSettingRepository extends JpaRepository<PayoutSetting, Long> {
    Optional<PayoutSetting> findByIdAndDeletedFalse(Long id);

    List<PayoutSetting> findAllByDeletedFalseOrderByValidFromAsc();

    boolean existsByValidFromAndDeletedFalse(LocalDate validFrom);

    boolean existsByValidFromAndIdNotAndDeletedFalse(LocalDate validFrom, Long id);
}
