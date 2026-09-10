package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.PushSubscription;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.service.dto.PushSubscriptionDTO;
import com.fundaro.zodiac.taurus.service.mapper.PushSubscriptionMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PushSubscriptionServiceImplTest {

    @Test
    void updatesAnExistingSubscriptionInsteadOfInsertingADuplicate() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        PushSubscriptionMapper mapper = mock(PushSubscriptionMapper.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query lockQuery = mock(Query.class);
        PushSubscriptionServiceImpl service = new PushSubscriptionServiceImpl(repository, mapper, entityManager);
        JwtAuthenticationToken token = authentication();
        PushSubscription existing = new PushSubscription();
        existing.setId(7L);
        existing.setUserId("user-1");
        existing.setEndpoint("https://push.example/subscription");
        existing.setP256dh("old-key");
        existing.setAuth("old-auth");
        existing.setDeleted(true);
        PushSubscriptionDTO request = new PushSubscriptionDTO();
        request.setEndpoint(existing.getEndpoint());
        request.setP256dh("new-key");
        request.setAuth("new-auth");
        PushSubscriptionDTO response = new PushSubscriptionDTO();
        response.setId(existing.getId());

        when(
            entityManager.createNativeQuery(
                "select 1 from pg_advisory_xact_lock(hashtext(current_schema() || ':' || :userId), hashtext(:endpoint))"
            )
        ).thenReturn(lockQuery);
        when(lockQuery.setParameter("userId", "user-1")).thenReturn(lockQuery);
        when(lockQuery.setParameter("endpoint", "https://push.example/subscription")).thenReturn(lockQuery);
        when(repository.findByUserIdAndEndpoint("user-1", existing.getEndpoint())).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toDto(existing)).thenReturn(response);

        assertThat(service.subscribe(request, token)).isSameAs(response);

        verify(lockQuery).getSingleResult();
        verify(repository, never()).deleteById(existing.getId());
        verify(mapper, never()).toEntity(request);
        assertThat(existing.getP256dh()).isEqualTo("new-key");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getDeleted()).isFalse();
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
