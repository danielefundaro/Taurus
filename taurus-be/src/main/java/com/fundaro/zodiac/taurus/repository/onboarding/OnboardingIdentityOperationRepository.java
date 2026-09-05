package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnboardingIdentityOperationRepository extends JpaRepository<OnboardingIdentityOperation, Long> {
    List<OnboardingIdentityOperation> findAllByJob_IdOrderByRow_RowNumberDesc(Long jobId);
    Optional<OnboardingIdentityOperation> findByRow_Id(Long rowId);
}
