package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.IssuerSerial;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TSPException;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Seals the signed documents with the certificate of the installation: a PAdES baseline signature (B-B, or B-T when a
 * time-stamping service is configured) appended as an incremental update, so the signatures already in the file stay
 * valid.
 *
 * <p>The key comes from the PKCS#12 keystore of {@code application.signature.seal}. Without a keystore, and only when
 * {@code ephemeral} is set, a temporary self-signed certificate is generated at startup for development.
 */
@Service
public class SignatureSealService {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureSealService.class);
    private static final Duration TSA_TIMEOUT = Duration.ofSeconds(10);
    /**
     * Room for the CMS signature, the certificate chain and a time-stamp token.
     */
    private static final int SIGNATURE_SIZE = 32 * 1024;

    private final ApplicationProperties.SealProperties properties;
    private final SecureRandom random = new SecureRandom();
    private volatile SealKey key;

    public SignatureSealService(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getSignature().getSeal();
    }

    @PostConstruct
    void load() {
        try {
            key = loadKey();
        } catch (GeneralSecurityException | IOException | OperatorCreationException | RuntimeException exception) {
            LOG.error("signature_seal_unavailable: unable to load the seal keystore", exception);
            key = null;
        }
    }

    /**
     * Whether documents can be sealed: a keystore, or the development certificate, is available.
     */
    public boolean available() {
        return key != null;
    }

    /**
     * SHA-256 fingerprint of the seal certificate, printed in the attestations.
     */
    public String certificateFingerprint() {
        return requiredKey().fingerprint();
    }

    public String certificateSubject() {
        return requiredKey().certificate().getSubjectX500Principal().getName();
    }

    /**
     * Appends the seal to a PDF and returns the new file, which starts with the bytes of the original one.
     */
    public byte[] seal(byte[] pdf, String reason) {
        SealKey sealKey = requiredKey();
        try (PDDocument document = Loader.loadPDF(pdf); SignatureOptions options = new SignatureOptions();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName(commonName(sealKey.certificate()));
            signature.setReason(reason);
            signature.setSignDate(Calendar.getInstance());
            options.setPreferredSignatureSize(SIGNATURE_SIZE);
            document.addSignature(signature, content -> cms(content, sealKey), options);
            document.saveIncremental(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to seal the document", exception);
        }
    }

    private byte[] cms(InputStream content, SealKey sealKey) throws IOException {
        try {
            X509CertificateHolder holder = new X509CertificateHolder(sealKey.certificate().getEncoded());
            ContentSigner signer = new JcaContentSignerBuilder(algorithm(sealKey.privateKey())).build(sealKey.privateKey());
            JcaSignerInfoGeneratorBuilder signerInfo = new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build());
            signerInfo.setSignedAttributeGenerator(signedAttributes(holder, sealKey.certificate().getEncoded()));
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(signerInfo.build(signer, holder));
            generator.addCertificates(new JcaCertStore(sealKey.chain()));
            CMSSignedData signed = generator.generate(new CMSProcessableByteArray(content.readAllBytes()), false);
            if (properties.getTsaUrl() != null && !properties.getTsaUrl().isBlank()) signed = withTimeStamp(signed);
            return signed.getEncoded("DER");
        } catch (GeneralSecurityException | OperatorCreationException | CMSException | TSPException exception) {
            throw new IOException("Unable to create the seal signature", exception);
        }
    }

    /**
     * Signed attributes of a PAdES baseline signature: content type, message digest and signing certificate v2, without
     * the signing time, which PAdES takes from the signature dictionary.
     */
    private static CMSAttributeTableGenerator signedAttributes(X509CertificateHolder holder, byte[] encodedCertificate) throws GeneralSecurityException {
        byte[] certificateHash = MessageDigest.getInstance("SHA-256").digest(encodedCertificate);
        ESSCertIDv2 certId = new ESSCertIDv2(
            new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256),
            certificateHash,
            new IssuerSerial(new GeneralNames(new GeneralName(holder.getIssuer())), holder.getSerialNumber())
        );
        ASN1EncodableVector attributes = new ASN1EncodableVector();
        attributes.add(new Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, new DERSet(new SigningCertificateV2(new ESSCertIDv2[] { certId }))));
        DefaultSignedAttributeTableGenerator standard = new DefaultSignedAttributeTableGenerator(new AttributeTable(attributes));
        return parameters -> standard.getAttributes(parameters).remove(CMSAttributes.signingTime);
    }

    private CMSSignedData withTimeStamp(CMSSignedData signed) throws IOException, TSPException, GeneralSecurityException {
        List<SignerInformation> signers = new java.util.ArrayList<>();
        for (SignerInformation signer : signed.getSignerInfos().getSigners()) {
            TimeStampToken token = timeStamp(signer.getSignature());
            ASN1EncodableVector unsigned = new ASN1EncodableVector();
            unsigned.add(new Attribute(PKCSObjectIdentifiers.id_aa_signatureTimeStampToken, new DERSet(ASN1Primitive.fromByteArray(token.getEncoded()))));
            signers.add(SignerInformation.replaceUnsignedAttributes(signer, new AttributeTable(unsigned)));
        }
        return CMSSignedData.replaceSigners(signed, new SignerInformationStore(signers));
    }

    private TimeStampToken timeStamp(byte[] signatureValue) throws IOException, TSPException, GeneralSecurityException {
        TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
        generator.setCertReq(true);
        TimeStampRequest request = generator.generate(
            TSPAlgorithms.SHA256,
            MessageDigest.getInstance("SHA-256").digest(signatureValue),
            new BigInteger(64, random)
        );
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(properties.getTsaUrl().trim()))
            .timeout(TSA_TIMEOUT)
            .header("Content-Type", "application/timestamp-query")
            .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()))
            .build();
        HttpResponse<byte[]> response;
        try {
            response = HttpClient.newBuilder().connectTimeout(TSA_TIMEOUT).build().send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the time-stamping service", exception);
        }
        if (response.statusCode() != 200) throw new IOException("The time-stamping service answered " + response.statusCode());
        TimeStampResponse timeStampResponse = new TimeStampResponse(response.body());
        timeStampResponse.validate(request);
        if (timeStampResponse.getTimeStampToken() == null) throw new IOException("The time-stamping service returned no token");
        return timeStampResponse.getTimeStampToken();
    }

    private SealKey loadKey() throws GeneralSecurityException, IOException, OperatorCreationException {
        String path = properties.getKeystorePath();
        if (path != null && !path.isBlank()) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            char[] password = properties.getKeystorePassword() == null ? new char[0] : properties.getKeystorePassword().toCharArray();
            try (InputStream input = Files.newInputStream(Path.of(path.trim()))) {
                keyStore.load(input, password);
            }
            String alias = alias(keyStore);
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            Certificate[] chain = keyStore.getCertificateChain(alias);
            if (privateKey == null || chain == null || chain.length == 0)
                throw new GeneralSecurityException("The seal keystore has no private key with a certificate chain");
            List<X509Certificate> certificates = Arrays.stream(chain).map(X509Certificate.class::cast).toList();
            SealKey loaded = new SealKey(privateKey, certificates);
            LOG.info(
                "signature_seal_loaded subject={} fingerprint={} notAfter={}",
                loaded.certificate().getSubjectX500Principal().getName(),
                loaded.fingerprint(),
                loaded.certificate().getNotAfter().toInstant()
            );
            return loaded;
        }
        if (properties.isEphemeral()) {
            LOG.warn("signature_seal_ephemeral: documents are sealed with a temporary self-signed certificate, for development only");
            return ephemeralKey();
        }
        LOG.info("signature_seal_not_configured: electronic signature cannot be enabled without a seal keystore");
        return null;
    }

    private String alias(KeyStore keyStore) throws GeneralSecurityException {
        String configured = properties.getKeyAlias();
        if (configured != null && !configured.isBlank()) return configured.trim();
        for (Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
            String alias = aliases.nextElement();
            if (keyStore.isKeyEntry(alias)) return alias;
        }
        throw new GeneralSecurityException("The seal keystore has no key entry");
    }

    static SealKey ephemeralKey() throws GeneralSecurityException, OperatorCreationException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=Taurus - sigillo di sviluppo,O=Taurus");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
            subject,
            new BigInteger(64, new SecureRandom()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject,
            pair.getPublic()
        );
        try {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        } catch (IOException exception) {
            throw new GeneralSecurityException("Unable to build the development seal certificate", exception);
        }
        X509Certificate certificate = new JcaX509CertificateConverter()
            .getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate())));
        return new SealKey(pair.getPrivate(), Collections.singletonList(certificate));
    }

    private SealKey requiredKey() {
        SealKey value = key;
        if (value == null) throw new IllegalStateException("No seal certificate is configured");
        return value;
    }

    private static String algorithm(PrivateKey key) {
        return switch (key.getAlgorithm()) {
            case "EC", "ECDSA" -> "SHA256withECDSA";
            default -> "SHA256withRSA";
        };
    }

    private static String commonName(X509Certificate certificate) {
        String name = certificate.getSubjectX500Principal().getName();
        for (String part : name.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) return trimmed.substring(3);
        }
        return name;
    }

    /**
     * Uses the given key instead of the configured one; for tests.
     */
    void useKey(SealKey value) {
        key = value;
    }

    record SealKey(PrivateKey privateKey, List<X509Certificate> chain) {
        X509Certificate certificate() {
            return chain.get(0);
        }

        String fingerprint() {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate().getEncoded()));
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("Unable to compute the seal certificate fingerprint", exception);
            }
        }

        @Override
        public String toString() {
            return "SealKey[subject=" + certificate().getSubjectX500Principal().getName() + "]";
        }
    }
}
