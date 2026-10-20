package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignatureSealServiceTest {

    private ApplicationProperties properties;
    private SignatureSealService service;

    @BeforeEach
    void setUp() throws Exception {
        properties = new ApplicationProperties();
        service = new SignatureSealService(properties);
        service.useKey(SignatureSealService.ephemeralKey());
    }

    @Test
    void appendsAValidPadesSignatureAfterTheOriginalBytes() throws Exception {
        byte[] pdf = pdf();

        byte[] sealed = service.seal(pdf, "Sigillo di prova");

        assertThat(Arrays.copyOf(sealed, pdf.length)).isEqualTo(pdf);
        List<SignerInformation> signers = verifiedSigners(sealed);
        assertThat(signers).hasSize(1);
        SignerInformation signer = signers.get(0);
        assertThat(signer.getSignedAttributes().get(PKCSObjectIdentifiers.id_aa_signingCertificateV2)).isNotNull();
        assertThat(signer.getSignedAttributes().get(CMSAttributes.signingTime)).isNull();
        assertThat(signer.getUnsignedAttributes()).isNull();
        try (PDDocument document = Loader.loadPDF(sealed)) {
            PDSignature signature = document.getLastSignatureDictionary();
            assertThat(signature.getSubFilter()).isEqualTo(PDSignature.SUBFILTER_ETSI_CADES_DETACHED.getName());
            assertThat(signature.getReason()).isEqualTo("Sigillo di prova");
            int[] range = signature.getByteRange();
            assertThat(range[2] + range[3]).isEqualTo(sealed.length);
        }
    }

    @Test
    void keepsTheSignaturesAlreadyInTheFileValid() throws Exception {
        byte[] signedByTheHolder = service.seal(pdf(), "Firma precedente");

        byte[] sealed = service.seal(signedByTheHolder, "Sigillo");

        assertThat(Arrays.copyOf(sealed, signedByTheHolder.length)).isEqualTo(signedByTheHolder);
        assertThat(verifiedSigners(sealed)).hasSize(2);
    }

    @Test
    void addsATimeStampWhenAServiceIsConfigured() throws Exception {
        HttpServer server = timeStampingService();
        try {
            properties.getSignature().getSeal().setTsaUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/tsa");

            byte[] sealed = service.seal(pdf(), "Sigillo");

            SignerInformation signer = verifiedSigners(sealed).get(0);
            var attribute = signer.getUnsignedAttributes().get(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken);
            assertThat(attribute).isNotNull();
            TimeStampToken token = new TimeStampToken(new CMSSignedData(attribute.getAttrValues().getObjectAt(0).toASN1Primitive().getEncoded()));
            assertThat(token.getTimeStampInfo().getMessageImprintDigest())
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(signer.getSignature()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void isUnavailableWithoutAKeystoreUnlessTheDevelopmentCertificateIsAllowed() {
        SignatureSealService unconfigured = new SignatureSealService(new ApplicationProperties());
        unconfigured.load();
        assertThat(unconfigured.available()).isFalse();
        assertThatThrownBy(() -> unconfigured.seal(new byte[0], "x")).isInstanceOf(IllegalStateException.class);

        ApplicationProperties development = new ApplicationProperties();
        development.getSignature().getSeal().setEphemeral(true);
        SignatureSealService ephemeral = new SignatureSealService(development);
        ephemeral.load();
        assertThat(ephemeral.available()).isTrue();
        assertThat(ephemeral.certificateFingerprint()).hasSize(64);
    }

    @Test
    void reportsAnUnreadableKeystoreAsUnavailable() {
        ApplicationProperties broken = new ApplicationProperties();
        broken.getSignature().getSeal().setKeystorePath("does-not-exist.p12");
        broken.getSignature().getSeal().setEphemeral(true);
        SignatureSealService service = new SignatureSealService(broken);
        service.load();

        assertThat(service.available()).isFalse();
    }

    static byte[] pdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * Verifies every signature of the file with the certificate it carries and returns their signer information.
     */
    static List<SignerInformation> verifiedSigners(byte[] pdf) throws Exception {
        List<SignerInformation> signers = new java.util.ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDSignature signature : document.getSignatureDictionaries()) {
                // The signature is padded with zeros up to the reserved size: only the first ASN.1 object is the CMS.
                ContentInfo contentInfo;
                try (ASN1InputStream input = new ASN1InputStream(signature.getContents(pdf))) {
                    contentInfo = ContentInfo.getInstance(input.readObject());
                }
                CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signature.getSignedContent(pdf)), contentInfo);
                SignerInformation signer = cms.getSignerInfos().getSigners().iterator().next();
                @SuppressWarnings("unchecked")
                X509CertificateHolder certificate = (X509CertificateHolder) cms.getCertificates().getMatches(signer.getSID()).iterator().next();
                assertThat(signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate))).isTrue();
                signers.add(signer);
            }
        }
        return signers;
    }

    private static HttpServer timeStampingService() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=TSA di prova");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject, BigInteger.ONE, Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)), subject, pair.getPublic()
        );
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        X509CertificateHolder certificate = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()));
        TimeStampTokenGenerator tokenGenerator = new TimeStampTokenGenerator(
            new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()), certificate),
            new JcaDigestCalculatorProviderBuilder().build().get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
            new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4")
        );
        TimeStampResponseGenerator responseGenerator = new TimeStampResponseGenerator(tokenGenerator, TSPAlgorithms.ALLOWED);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tsa", exchange -> {
            try {
                TimeStampRequest request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
                byte[] response = responseGenerator.generate(request, BigInteger.TWO, new Date()).getEncoded();
                exchange.getResponseHeaders().set("Content-Type", "application/timestamp-reply");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(response);
                }
            } catch (Exception exception) {
                exchange.sendResponseHeaders(500, -1);
            }
        });
        server.start();
        return server;
    }
}
