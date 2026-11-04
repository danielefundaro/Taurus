package com.fundaro.zodiac.taurus.repository.guidedtour;

import com.fundaro.zodiac.taurus.domain.guidedtour.GuidedTourProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuidedTourProfileRepository extends JpaRepository<GuidedTourProfile, Long> {

    Optional<GuidedTourProfile> findByKeycloakSubjectAndDeletedFalse(String keycloakSubject);

    /** Bulk delete: the progress rows follow the profile with {@code ON DELETE CASCADE}. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM GuidedTourProfile profile WHERE profile.keycloakSubject = :subject")
    int deleteAllByKeycloakSubject(@Param("subject") String keycloakSubject);
}
