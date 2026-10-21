package com.fundaro.zodiac.taurus.service.signature;

import static com.fundaro.zodiac.taurus.service.signature.CieSignTestSupport.OTHER_TAX_CODE;
import static com.fundaro.zodiac.taurus.service.signature.CieSignTestSupport.TAX_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.service.signature.CieSignTestSupport.Authority;
import com.fundaro.zodiac.taurus.service.signature.CieSignTestSupport.Issued;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CRLException;
import java.security.cert.X509CRL;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class CieSignVerifierTest {

    private static final CieSignVerifier.Identity MARIO = new CieSignVerifier.Identity("Mario Rossi", TAX_CODE);

    private static Authority cie;
    private static byte[] frozen;
    private static Issued mario;

    private final AtomicReference<X509CRL> published = new AtomicReference<>();
    private HttpServer server;
    private CieSignVerifier verifier;

    @BeforeAll
    static void createAuthority() {
        cie = CieSignTestSupport.authority("CIE di prova");
        frozen = CieSignTestSupport.frozenPdf();
        mario = cie.holder("MARIO", "ROSSI", TAX_CODE, null);
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/crl", exchange -> {
            byte[] body;
            try {
                body = published.get().getEncoded();
            } catch (CRLException exception) {
                throw new IOException(exception);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        verifier = verifier(Duration.ofSeconds(2));
        verifier.useTrustAnchors(List.of(cie.root().certificate()));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void acceptsTheFrozenDocumentSignedWithACieCertificate() {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));

        CieSignVerifier.Result result = verifier.verify(frozen, signed, MARIO, Instant.now());

        assertThat(result.taxCode()).isEqualTo(TAX_CODE);
        assertThat(result.subject()).contains("SERIALNUMBER=TINIT-" + TAX_CODE.substring(0, 6)).doesNotContain("\n");
        assertThat(result.issuer()).contains("CIE di prova Sub CA");
        assertThat(result.serial()).isEqualTo(java.util.HexFormat.of().withUpperCase().formatHex(mario.certificate().getSerialNumber().toByteArray()));
        assertThat(result.revocationCheck()).isEqualTo(CieRevocationChecker.UNAVAILABLE);
    }

    @Test
    void comparesTheNameWithoutCaseAccentsSpacesAndOrder() {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));

        assertThat(verifier.verify(frozen, signed, new CieSignVerifier.Identity("  màrio   rossi ", TAX_CODE.toLowerCase()), Instant.now()).taxCode()).isEqualTo(TAX_CODE);
        assertThat(verifier.verify(frozen, signed, new CieSignVerifier.Identity("Rossi Mario", TAX_CODE), Instant.now()).taxCode()).isEqualTo(TAX_CODE);
        Issued apostrophe = cie.holder("LUCA", "D'ANGELO", TAX_CODE, null);
        byte[] other = CieSignTestSupport.sign(frozen, apostrophe, cie.chain(apostrophe));
        assertThat(verifier.verify(frozen, other, new CieSignVerifier.Identity("Luca D’Angelo", TAX_CODE), Instant.now()).taxCode()).isEqualTo(TAX_CODE);
    }

    @Test
    void refusesAFileThatDoesNotStartWithTheFrozenDocument() {
        byte[] another = CieSignTestSupport.frozenPdf();
        byte[] signed = CieSignTestSupport.sign(another, mario, cie.chain(mario));

        assertRejected(() -> verifier.verify(frozen, signed, MARIO, Instant.now()), "signature.cieSign.documentMismatch");
        assertRejected(() -> verifier.verify(frozen, "not a PDF".getBytes(), MARIO, Instant.now()), "signature.cieSign.documentMismatch");
    }

    @Test
    void refusesTheFrozenDocumentUploadedWithoutASignature() {
        assertRejected(() -> verifier.verify(frozen, frozen, MARIO, Instant.now()), "signature.cieSign.invalidSignature");
    }

    @Test
    void refusesAFileChangedAfterTheSignature() throws IOException {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));
        byte[] changed;
        try (PDDocument document = Loader.loadPDF(signed); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Modificato");
            document.getDocumentInformation().getCOSObject().setNeedToBeUpdated(true);
            document.saveIncremental(output);
            changed = output.toByteArray();
        }

        assertRejected(() -> verifier.verify(frozen, changed, MARIO, Instant.now()), "signature.cieSign.documentMismatch");
    }

    @Test
    void refusesAnUpdateThatChangesThePagesTogetherWithTheSignature() {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario), document -> {
            PDPage page = document.getPage(0);
            try (PDPageContentStream content = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true)) {
                content.addRect(72, 690, 300, 20);
                content.fill();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            page.getCOSObject().setNeedToBeUpdated(true);
        });

        assertRejected(() -> verifier.verify(frozen, signed, MARIO, Instant.now()), "signature.cieSign.documentMismatch");
    }

    @Test
    void refusesAnUpdateThatChangesTheCatalog() {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario), document -> {
            document.getDocumentCatalog().getCOSObject().setItem(COSName.PAGE_LAYOUT, COSName.getPDFName("TwoColumnLeft"));
            document.getDocumentCatalog().getCOSObject().setNeedToBeUpdated(true);
        });

        assertRejected(() -> verifier.verify(frozen, signed, MARIO, Instant.now()), "signature.cieSign.documentMismatch");
    }

    @Test
    void refusesTwoSignaturesOrATamperedSignature() {
        byte[] once = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));
        byte[] twice = CieSignTestSupport.sign(once, mario, cie.chain(mario));
        assertRejected(() -> verifier.verify(frozen, twice, MARIO, Instant.now()), "signature.cieSign.invalidSignature");

        byte[] tampered = once.clone();
        int name = indexOf(tampered, "Firma CIE".getBytes(StandardCharsets.US_ASCII), frozen.length);
        tampered[name] = 'f';
        assertRejected(() -> verifier.verify(frozen, tampered, MARIO, Instant.now()), "signature.cieSign.invalidSignature");
    }

    @Test
    void refusesACertificateNotIssuedByTheCieOrExpired() {
        Authority other = CieSignTestSupport.authority("Altra CA");
        Issued stranger = other.holder("MARIO", "ROSSI", TAX_CODE, null);
        byte[] signed = CieSignTestSupport.sign(frozen, stranger, other.chain(stranger));
        assertRejected(() -> verifier.verify(frozen, signed, MARIO, Instant.now()), "signature.cieSign.untrustedCertificate");

        Issued expired = cie.holder("MARIO", "ROSSI", TAX_CODE, null, Instant.now().minus(1, ChronoUnit.HOURS));
        byte[] old = CieSignTestSupport.sign(frozen, expired, cie.chain(expired));
        assertRejected(() -> verifier.verify(frozen, old, MARIO, Instant.now()), "signature.cieSign.untrustedCertificate");
    }

    @Test
    void refusesTheCertificateOfAnotherPerson() {
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));

        assertRejected(() -> verifier.verify(frozen, signed, new CieSignVerifier.Identity("Maria Rossi", TAX_CODE), Instant.now()), "signature.cieSign.signerMismatch");
        assertRejected(() -> verifier.verify(frozen, signed, new CieSignVerifier.Identity("Mario Rossi", OTHER_TAX_CODE), Instant.now()), "signature.cieSign.signerMismatch");
    }

    @Test
    void checksTheRevocationListNamedInTheCertificate() {
        Issued listed = cie.holder("MARIO", "ROSSI", TAX_CODE, "http://127.0.0.1:" + server.getAddress().getPort() + "/crl");
        byte[] signed = CieSignTestSupport.sign(frozen, listed, cie.chain(listed));

        published.set(CieSignTestSupport.crl(cie));
        assertThat(verifier.verify(frozen, signed, MARIO, Instant.now()).revocationCheck()).isEqualTo(CieRevocationChecker.GOOD);

        published.set(CieSignTestSupport.crl(cie, listed.certificate()));
        CieSignVerifier fresh = verifier(Duration.ofSeconds(2));
        fresh.useTrustAnchors(List.of(cie.root().certificate()));
        assertRejected(() -> fresh.verify(frozen, signed, MARIO, Instant.now()), "signature.cieSign.untrustedCertificate");
    }

    @Test
    void acceptsTheSignatureWhenTheRevocationListCannotBeReached() {
        Issued unreachable = cie.holder("MARIO", "ROSSI", TAX_CODE, "http://127.0.0.1:1/crl");
        byte[] signed = CieSignTestSupport.sign(frozen, unreachable, cie.chain(unreachable));

        assertThat(verifier.verify(frozen, signed, MARIO, Instant.now()).revocationCheck()).isEqualTo(CieRevocationChecker.UNAVAILABLE);
    }

    @Test
    void loadsTheTrustAnchorsFromTheConfiguredFiles(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("cie.pem");
        String pem = "-----BEGIN CERTIFICATE-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(cie.root().certificate().getEncoded())
            + "\n-----END CERTIFICATE-----\n";
        Files.writeString(file, pem);
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().getCie().setTrustAnchors(List.of(" ", file.toString(), directory.resolve("missing.pem").toString()));
        CieSignVerifier configured = new CieSignVerifier(properties, new CieRevocationChecker(properties));
        configured.load();

        assertThat(configured.available()).isTrue();
        byte[] signed = CieSignTestSupport.sign(frozen, mario, cie.chain(mario));
        assertThat(configured.verify(frozen, signed, MARIO, Instant.now()).taxCode()).isEqualTo(TAX_CODE);
    }

    @Test
    void isNotAvailableWithoutTrustAnchors() {
        ApplicationProperties properties = new ApplicationProperties();
        CieSignVerifier empty = new CieSignVerifier(properties, new CieRevocationChecker(properties));
        empty.load();

        assertThat(empty.available()).isFalse();
        assertRejected(() -> empty.verify(frozen, frozen, MARIO, Instant.now()), "signature.cieSign.notConfigured");
    }

    private static CieSignVerifier verifier(Duration timeout) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().getCie().setRevocationTimeout(timeout);
        return new CieSignVerifier(properties, new CieRevocationChecker(properties));
    }

    private static void assertRejected(ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo(key));
    }

    private static int indexOf(byte[] content, byte[] value, int from) {
        outer:
        for (int index = from; index <= content.length - value.length; index++) {
            for (int offset = 0; offset < value.length; offset++) {
                if (content[index + offset] != value[offset]) continue outer;
            }
            return index;
        }
        throw new IllegalArgumentException("Not found");
    }
}
