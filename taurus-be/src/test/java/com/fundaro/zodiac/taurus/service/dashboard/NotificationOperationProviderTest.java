package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliverySummary;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Le consegne tecniche fallite sono amministrative e non espongono contenuto, destinatari o errori.
 */
class NotificationOperationProviderTest {

    private final ZonedDateTime now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("Europe/Rome"));
    private final NotificationDeliveryAdminQueryRepository repository = mock(NotificationDeliveryAdminQueryRepository.class);
    private final NotificationOperationProvider provider = new NotificationOperationProvider(repository);

    @Test
    void nonAdministratorsRunNoQuery() {
        for (String authority : List.of(
            AuthoritiesConstants.TREASURER,
            AuthoritiesConstants.ARCHIVIST,
            AuthoritiesConstants.USER,
            AuthoritiesConstants.USER_EXTERNAL
        )) {
            assertThat(provider.getOperations(context(authority))).as(authority).isEmpty();
        }
        verifyNoInteractions(repository);
    }

    @Test
    void administratorsSeeTheFailuresOfTheThreeOriginsAsOneDangerRow() {
        ZonedDateTime oldest = now.minusHours(5);
        when(repository.summarize(NotificationStatus.FAILED)).thenReturn(new NotificationDeliverySummary(4, oldest));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.ADMIN)).get(0);

        assertThat(item.type()).isEqualTo(DashboardOperationType.NOTIFICATION_DELIVERY_FAILED);
        assertThat(item.domain()).isEqualTo(DashboardDomain.NOTIFICATIONS);
        assertThat(item.severity()).isEqualTo(DashboardSeverity.DANGER);
        assertThat(item.count()).isEqualTo(4);
        assertThat(item.dueAt()).isEqualTo(oldest);
        assertThat(item.targetPath()).isEqualTo("/admin/notification-delivery?status=FAILED");
        // Alla riga arrivano solo metadati aggregati: nessun titolo, destinatario o eccezione di una consegna.
        assertThat(item.description()).isEqualTo("Eventi tecnici da riprocessare; contenuti e destinatari non sono mostrati.");
    }

    @Test
    void producesNoRowWithoutFailures() {
        when(repository.summarize(NotificationStatus.FAILED)).thenReturn(new NotificationDeliverySummary(0, null));

        assertThat(provider.getOperations(context(AuthoritiesConstants.SUPER_ADMIN))).isEmpty();
    }

    private DashboardRequestContext context(String authority) {
        return new DashboardRequestContext("subject", Set.of(authority), now, now.getZone(), mock(AbstractAuthenticationToken.class));
    }
}
