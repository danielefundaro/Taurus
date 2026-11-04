package com.fundaro.zodiac.taurus.repository.guidedtour;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProfile;
import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuidedTourProgressRepository extends JpaRepository<GuidedTourProgress, Long> {

    Optional<GuidedTourProgress> findByProfileAndRoleAndDeletedFalse(GuidedTourProfile profile, RoleEnum role);
}
