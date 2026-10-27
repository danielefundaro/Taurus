package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La voce legale è difensiva: usa la stessa regola di {@code GET /api/legal/status}, per ogni ruolo.
 */
class LegalOperationProviderTest {

    private final LegalService legalService = mock(LegalService.class);
    private final LegalOperationProvider provider = new LegalOperationProvider(legalService);

    @Test
    void aCompliantUserHasNoLegalOperation() {
        AbstractAuthenticationToken token = mock(AbstractAuthenticationToken.class);
        when(legalService.getStatus(token)).thenReturn(new LegalStatusDTO(true, List.of(document(true, true))));

        assertThat(provider.getOperations(context(token, AuthoritiesConstants.USER))).isEmpty();
        verify(legalService).getStatus(token);
    }

    @Test
    void aNonCompliantUserOfAnyRoleIsSentToTheAcceptancePage() {
        for (String authority : List.of(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.TREASURER, AuthoritiesConstants.USER_EXTERNAL)) {
            AbstractAuthenticationToken token = mock(AbstractAuthenticationToken.class);
            when(legalService.getStatus(token)).thenReturn(new LegalStatusDTO(false, List.of(document(true, false), document(true, true), document(false, false))));

            OperationalItemDTO item = provider.getOperations(context(token, authority)).get(0);

            assertThat(item.type()).as(authority).isEqualTo(DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED);
            assertThat(item.domain()).isEqualTo(DashboardDomain.LEGAL);
            assertThat(item.severity()).isEqualTo(DashboardSeverity.DANGER);
            // Contano solo i documenti obbligatori ancora da accettare, non quelli facoltativi.
            assertThat(item.count()).isEqualTo(1);
            assertThat(item.dueAt()).isNull();
            assertThat(item.targetPath()).isEqualTo("/legal/accept");
        }
    }

    private static LegalDocumentStatusDTO document(boolean required, boolean accepted) {
        return new LegalDocumentStatusDTO(
            1L,
            LegalDocumentType.TERMS,
            "1.0",
            "Termini",
            "https://example.test/terms",
            LegalDocumentAction.ACCEPT,
            ZonedDateTime.now(),
            required,
            accepted,
            accepted ? ZonedDateTime.now() : null
        );
    }

    private static DashboardRequestContext context(AbstractAuthenticationToken token, String authority) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));
        return new DashboardRequestContext("subject", Set.of(authority), now, now.getZone(), token);
    }
}
