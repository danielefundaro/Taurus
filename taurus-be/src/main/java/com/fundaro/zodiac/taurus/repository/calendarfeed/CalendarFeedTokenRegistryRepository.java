package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CalendarFeedTokenRegistryRepository extends JpaRepository<CalendarFeedTokenRegistry, byte[]> {
    @Query(value = """
        select r.* from public.calendar_feed_token_registry r
        join public.tenant_schema_registry s on s.tenant_id = r.tenant_id
        join public.tenant t on t.id = r.tenant_id
        where r.token_digest = :digest and r.status = 'ACTIVE'
          and s.status = 'ACTIVE' and s.deleted = false
          and t.active = true and t.deleted = false
        """, nativeQuery = true)
    Optional<CalendarFeedTokenRegistry> resolveActive(@Param("digest") byte[] digest);

    @Modifying
    @Query("update CalendarFeedTokenRegistry r set r.status = 'REVOKED', r.revokedAt = :now where r.subscriptionId = :id and r.status = 'ACTIVE'")
    int revokeActive(@Param("id") UUID subscriptionId, @Param("now") java.time.Instant now);

    void deleteAllByTenantId(Long tenantId);
}
