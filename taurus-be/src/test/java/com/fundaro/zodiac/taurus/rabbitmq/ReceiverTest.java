package com.fundaro.zodiac.taurus.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.impl.PdfProcessingService;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.utils.Converter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ReceiverTest {

    @Test
    void activatesMessageTenantBeforeLoadingUploadJobAndRestoresContextAfterwards() throws Exception {
        QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
        TracksService tracksService = mock(TracksService.class);
        Receiver receiver = new Receiver(
            queueUploadFilesService,
            tracksService,
            mock(PdfProcessingService.class),
            mock(TenantStorageService.class)
        );
        JwtAuthenticationToken token = authentication();
        when(queueUploadFilesService.findOne(3L, token)).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantCode()).contains("BMCDG");
            return Optional.empty();
        });

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        assertThat(TenantContext.getTenantCode()).isEmpty();
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
