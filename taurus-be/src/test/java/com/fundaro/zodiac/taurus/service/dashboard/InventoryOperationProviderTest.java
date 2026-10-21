package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import com.fundaro.zodiac.taurus.service.signature.LoanSheetSignatureService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryOperationProviderTest {

    private final LoanSheetSignatureService signatureService = mock(LoanSheetSignatureService.class);

    @Test
    void asksTheUserForTheSignaturesOfTheLoanSheetsAsForTheAcknowledgments() {
        when(signatureService.countAwaitingUser("member")).thenReturn(2L);

        List<OperationalItemDTO> items = provider().getOperations(context("member", AuthoritiesConstants.USER));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo(DashboardOperationType.INVENTORY_SIGNATURE_REQUIRED);
            assertThat(item.severity()).isEqualTo(DashboardSeverity.WARNING);
            assertThat(item.count()).isEqualTo(2);
            assertThat(item.title()).isEqualTo("Firma richiesta");
            assertThat(item.description()).isEqualTo("2 atti dei fogli di affidamento attendono la tua firma.");
            assertThat(item.targetPath()).isEqualTo("/inventory?view=mine");
        });
    }

    @Test
    void asksAlsoTheAdministratorWhoSignsForTheAssociationAndShowsNothingWithoutActs() {
        when(signatureService.countAwaitingUser("admin")).thenReturn(1L);

        assertThat(provider().getOperations(context("admin", AuthoritiesConstants.ADMIN)))
            .extracting(OperationalItemDTO::description)
            .containsExactly("Un atto dei fogli di affidamento attende la tua firma.");
        assertThat(provider().getOperations(context("member", AuthoritiesConstants.USER))).isEmpty();
    }

    private InventoryOperationProvider provider() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getInventory().getQr().setEnabled(false);
        InventoryOperationProvider provider = new InventoryOperationProvider(
            mock(InventoryAssignmentRepository.class),
            mock(InventoryAssignmentDecisionRepository.class),
            mock(InventoryReturnRepository.class),
            properties,
            mock(InventoryIssueReportRepository.class)
        );
        provider.setSignatureService(signatureService);
        return provider;
    }

    private static DashboardRequestContext context(String subject, String authority) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));
        return new DashboardRequestContext(subject, Set.of(authority), now, now.getZone(), mock(AbstractAuthenticationToken.class));
    }
}
