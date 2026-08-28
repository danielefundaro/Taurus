package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.TenantUserMembership;
import com.fundaro.zodiac.taurus.domain.TenantUserMembershipId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantUserMembershipRepository extends JpaRepository<TenantUserMembership, TenantUserMembershipId> {}
