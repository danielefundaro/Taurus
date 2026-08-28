package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReportExportRepository;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class InventoryReportServiceTest {
    @Mock InventoryService inventoryService;
    @Mock UsersService usersService;
    @Mock TenantsService tenantsService;
    @Mock InventoryReportExportRepository reportExportRepository;
    @InjectMocks InventoryReportService reportService;

    @Test
    void shouldCreateReadablePdfForCurrentUser() throws Exception {
        UsersDTO user = new UsersDTO();
        user.setId(42L);
        user.setName("Mario");
        user.setLastName("Rossi");
        user.setEmail("mario.rossi@example.test");
        JwtAuthenticationToken token = authentication();
        when(usersService.findMe(token)).thenReturn(Optional.of(user));
        when(tenantsService.findByCode("tenant-a", token)).thenReturn(Optional.empty());
        ZonedDateTime now = ZonedDateTime.now();
        InventoryAssignmentDTO assignment = new InventoryAssignmentDTO(
            1L, 10L, "INV-2026-001", "Leggio orchestrale", "Leggio pieghevole in metallo con custodia protettiva",
            new java.math.BigDecimal("85.50"), "EUR", InventoryCondition.GOOD, "Normali segni d'uso",
            42L, "Mario", "Rossi", 1, 2, 1, 1, now.minusMonths(2), "Consegnato per prove e concerti",
            InventoryAssignmentStatus.PARTIALLY_RETURNED, 3, "a".repeat(64), now.minusDays(2),
            new InventoryDecisionDTO(InventoryDecisionType.ACCEPTED, null, now.minusDays(1)),
            List.of(new InventoryReturnDTO(2L, 1, InventoryReturnStatus.COMPLETED, now.minusDays(5), now.minusDays(4), InventoryCondition.GOOD, "Riconsegnato integro", List.of())),
            List.of()
        );
        when(inventoryService.findOwnAssignments(token)).thenReturn(List.of(assignment));

        var report = reportService.createOwn(true, true, false, token);

        assertThat(report.fileName()).endsWith(".pdf");
        assertThat(report.bytes()).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(report.bytes())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
        verify(reportExportRepository).save(argThat(export ->
            export.getFileSize() == report.bytes().length &&
            export.getContentDigest().length() == 64 &&
            export.getInsertBy().equals("user-1") &&
            export.getEditBy().equals("user-1") &&
            export.getInsertDate() != null &&
            export.getEditDate() != null &&
            !export.isDeleted()
        ));
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
