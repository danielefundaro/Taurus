package com.fundaro.zodiac.taurus.repository.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingIdentityOperation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OnboardingIdentityOperationRepository extends JpaRepository<OnboardingIdentityOperation, Long> {
    @EntityGraph(attributePaths = {"job", "row"})
    List<OnboardingIdentityOperation> findAllByJob_IdOrderByRow_RowNumberDesc(Long jobId);

    @EntityGraph(attributePaths = {"job", "row"})
    Optional<OnboardingIdentityOperation> findByRow_Id(Long rowId);
}
