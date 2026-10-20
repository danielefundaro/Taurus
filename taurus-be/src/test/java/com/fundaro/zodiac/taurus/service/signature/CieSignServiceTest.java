package com.fundaro.zodiac.taurus.service.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Requirements;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class CieSignServiceTest {

    private static final byte[] FROZEN = "%PDF-frozen".getBytes();
    private static final byte[] SIGNED = "%PDF-frozen-signed".getBytes();
    private static final CieSignVerifier.Identity IDENTITY = new CieSignVerifier.Identity("Mario Rossi", CieSignTestSupport.TAX_CODE);

    private final LoanSheetSigning signing = mock(LoanSheetSigning.class);
    private final CieSignVerifier verifier = mock(CieSignVerifier.class);
    private final SignatureSigners signers = mock(SignatureSigners.class);
    private final SignatureRequirementsService requirementsService = mock(SignatureRequirementsService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private CieSignService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(verifier.available()).thenReturn(true);
        when(requirementsService.requirements()).thenReturn(new Requirements(true, true, true));
        when(signing.cieSignTarget(anyLong(), any(), any())).thenReturn(new LoanSheetSigning.CieSignTarget("a".repeat(64), FROZEN, IDENTITY));
        service = new CieSignService(signing, verifier, signers, requirementsService, new SignatureMetrics(registry), transactionManager);
    }

    @Test
    void recordsTheSignatureVerifiedOutsideTheTransactions() {
        CieSignVerifier.Result result = new CieSignVerifier.Result("CN=x", "CN=CA", "01", CieSignTestSupport.TAX_CODE, CieRevocationChecker.GOOD);
        when(verifier.verify(eq(FROZEN), eq(SIGNED), eq(IDENTITY), any(Instant.class))).thenReturn(result);
        ActOutcome outcome = new ActOutcome(5L, SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.AWAITING_SIGNATURES, null);
        when(signing.recordCieSign(eq(5L), eq(SignatureDocumentRole.MAIN), eq("a".repeat(64)), eq(SIGNED), eq(result), any())).thenReturn(outcome);

        assertThat(service.sign(5L, SignatureDocumentRole.MAIN, SIGNED, new MockHttpServletRequest(), token())).isEqualTo(outcome);
        assertThat(registry.counter("taurus.signature.ciesign", "outcome", "accepted").count()).isEqualTo(1);
    }

    @Test
    void countsTheSignaturesAcceptedWithoutTheRevocationCheck() {
        CieSignVerifier.Result result = new CieSignVerifier.Result("CN=x", "CN=CA", "01", CieSignTestSupport.TAX_CODE, CieRevocationChecker.UNAVAILABLE);
        when(verifier.verify(any(), any(), any(), any())).thenReturn(result);
        when(signing.recordCieSign(anyLong(), any(), any(), any(), any(), any()))
            .thenReturn(new ActOutcome(5L, SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.SIGNED, null));

        service.sign(5L, SignatureDocumentRole.MAIN, SIGNED, new MockHttpServletRequest(), token());

        assertThat(registry.counter("taurus.signature.ciesign", "outcome", "acceptedRevocationUnavailable").count()).isEqualTo(1);
    }

    @Test
    void recordsNothingForARejectedFile() {
        when(verifier.verify(any(), any(), any(), any()))
            .thenThrow(SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Another person", "signature.cieSign.signerMismatch"));

        assertRejected(() -> service.sign(5L, SignatureDocumentRole.MAIN, SIGNED, new MockHttpServletRequest(), token()), "signature.cieSign.signerMismatch");
        verify(signing, never()).recordCieSign(anyLong(), any(), any(), any(), any(), any());
        assertThat(registry.counter("taurus.signature.ciesign", "outcome", "signerMismatch").count()).isEqualTo(1);
    }

    @Test
    void refusesCieSignWithoutTheCertificatesOfTheCie() {
        when(verifier.available()).thenReturn(false);

        assertRejected(() -> service.sign(5L, SignatureDocumentRole.MAIN, SIGNED, new MockHttpServletRequest(), token()), "signature.cieSign.notConfigured");
        verify(signing, never()).cieSignTarget(anyLong(), any(), any());
    }

    @Test
    void refusesAFileLargerThanTheLimitWithoutReadingIt() {
        MockMultipartFile large = new MockMultipartFile("file", "firmato.pdf", "application/pdf", new byte[1]) {
            @Override
            public long getSize() {
                return CieSignService.MAX_BYTES + 1;
            }
        };

        assertThatThrownBy(() -> CieSignService.content(large)).isInstanceOfSatisfying(RequestAlertException.class, exception -> {
            assertThat(exception.getErrorKey()).isEqualTo("signature.cieSign.tooLarge");
            assertThat(exception.getArgs().values()).containsEntry("maxMegabytes", 25);
        });
        assertRejected(() -> CieSignService.content(new MockMultipartFile("file", new byte[0])), "signature.cieSign.invalidSignature");
        assertThat(CieSignService.content(new MockMultipartFile("file", SIGNED))).isEqualTo(SIGNED);

        assertRejected(() -> service.sign(5L, SignatureDocumentRole.MAIN, large, new MockHttpServletRequest(), token()), "signature.cieSign.tooLarge");
        assertThat(registry.counter("taurus.signature.ciesign", "outcome", "tooLarge").count()).isEqualTo(1);
        verify(signing, never()).cieSignTarget(anyLong(), any(), any());
    }

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo(key));
    }

    private static JwtAuthenticationToken token() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("member-1").claims(claims -> claims.putAll(Map.of("tenant", "tenant-a")))
            .issuedAt(now).expiresAt(now.plusSeconds(60)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
