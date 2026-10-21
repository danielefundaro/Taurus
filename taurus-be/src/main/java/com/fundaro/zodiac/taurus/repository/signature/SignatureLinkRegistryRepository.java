package com.fundaro.zodiac.taurus.repository.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureLinkRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface SignatureLinkRegistryRepository extends JpaRepository<SignatureLinkRegistry, String> {

    /**
     * The registry row of a token not revoked and not expired, of a tenant and a schema both active.
     */
    @Query(value = """
        select r.* from public.signature_link_registry r
        join public.tenant_schema_registry s on s.tenant_id = r.tenant_id
        join public.tenant t on t.id = r.tenant_id
        where r.token_digest = :digest and r.revoked_at is null and r.expires_at > :now
          and s.status = 'ACTIVE' and s.deleted = false
          and t.active = true and t.deleted = false
        """, nativeQuery = true)
    Optional<SignatureLinkRegistry> resolveActive(@Param("digest") String digest, @Param("now") ZonedDateTime now);

    @Modifying
    @Query("update SignatureLinkRegistry r set r.revokedAt = :now where r.tenantId = :tenantId and r.linkId = :linkId and r.revokedAt is null")
    int revoke(@Param("tenantId") Long tenantId, @Param("linkId") Long linkId, @Param("now") ZonedDateTime now);

    /**
     * Removes the rows of revoked or expired links: the tokens can no longer be resolved.
     */
    @Modifying
    @Query("delete from SignatureLinkRegistry r where r.revokedAt is not null or r.expiresAt < :now")
    int deleteInactive(@Param("now") ZonedDateTime now);

    void deleteAllByTenantId(Long tenantId);
}
