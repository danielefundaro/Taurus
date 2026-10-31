package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.cert.CertPathBuilder;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXCertPathBuilderResult;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CertSelector;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.security.auth.x500.X500Principal;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Selector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Verification of a document signed with CieSign: the file must be the frozen document with one PAdES signature added,
 * valid, made with a certificate issued by a CIE certification authority configured as trust anchor and valid at the
 * upload, whose holder has the name and the tax code of the expected signer. The file is parsed, never executed.
 */
@Service
public class CieSignVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(CieSignVerifier.class);
    /**
     * Digest algorithms no longer accepted for a signature: SHA-1 and MD5.
     */
    private static final Set<String> WEAK_DIGESTS = Set.of("1.3.14.3.2.26", "1.2.840.113549.2.5");
    private static final Pattern TAX_CODE = Pattern.compile("[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]");
    private static final Pattern NAME_SEPARATORS = Pattern.compile("[\\s'’‘`´-]");

    /**
     * How long before the expiry of a trust anchor the startup warns about it.
     */
    private static final int EXPIRY_WARNING_DAYS = 60;

    private final ApplicationProperties.CieProperties properties;
    private final CieRevocationChecker revocationChecker;
    private volatile Set<TrustAnchor> anchors = Set.of();

    public CieSignVerifier(ApplicationProperties applicationProperties, CieRevocationChecker revocationChecker) {
        this.properties = applicationProperties.getSignature().getCie();
        this.revocationChecker = revocationChecker;
    }

    @PostConstruct
    void load() {
        Set<TrustAnchor> loaded = new LinkedHashSet<>();
        for (String location : properties.getTrustAnchors()) {
            if (location == null || location.isBlank()) continue;
            try (InputStream input = open(location.trim())) {
                for (Certificate certificate : CertificateFactory.getInstance("X.509").generateCertificates(input)) {
                    loaded.add(new TrustAnchor((X509Certificate) certificate, null));
                }
            } catch (IOException | CertificateException | RuntimeException exception) {
                LOG.error("signature_ciesign_trust_anchor_invalid location={}", location, exception);
            }
        }
        anchors = Set.copyOf(loaded);
        if (anchors.isEmpty()) {
            LOG.info("signature_ciesign_not_configured: CieSign is not offered without the certificates of the CIE");
        } else {
            Instant now = Instant.now();
            anchors.forEach(anchor -> {
                X509Certificate certificate = anchor.getTrustedCert();
                String subject = certificate.getSubjectX500Principal().getName();
                Instant notAfter = certificate.getNotAfter().toInstant();
                LOG.info("signature_ciesign_trust_anchor subject={} notAfter={}", subject, notAfter);
                long days = daysToExpiry(certificate, now);
                if (days < 0) {
                    LOG.error("signature_ciesign_trust_anchor_expired subject={} notAfter={}", subject, notAfter);
                } else if (days <= EXPIRY_WARNING_DAYS) {
                    LOG.warn("signature_ciesign_trust_anchor_expiring subject={} notAfter={} days={}", subject, notAfter, days);
                }
            });
        }
    }

    /**
     * Days left before the certificate expires, negative once it has. A CIE authority close to its renewal is worth a
     * warning at the startup: as soon as it is no longer valid, the cards it issued are refused.
     */
    static long daysToExpiry(X509Certificate certificate, Instant at) {
        return ChronoUnit.DAYS.between(at, certificate.getNotAfter().toInstant());
    }

    /**
     * Whether CieSign can be offered: the certificates of the CIE are configured.
     */
    public boolean available() {
        return !anchors.isEmpty();
    }

    /**
     * Verifies the file uploaded for a frozen document and returns the data of the certificate for the evidence.
     */
    public Result verify(byte[] frozen, byte[] signed, Identity expected, Instant at) {
        if (!available()) throw notConfigured();
        CieSignPdfInspector.SignedPart part = CieSignPdfInspector.inspect(frozen, signed);
        SignerData signer = signer(part);
        X509Certificate certificate = signer.certificate();
        X509Certificate issuer = issuer(certificate, signer.certificates(), at);
        boolean[] keyUsage = certificate.getKeyUsage();
        if (keyUsage != null && !(keyUsage[0] || keyUsage[1])) throw untrusted("The certificate cannot sign");
        X500Name subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        String taxCode = taxCode(subject);
        if (!sameName(expected.name(), subject) || taxCode == null || !taxCode.equals(ItalianTaxCode.normalize(expected.taxCode()))) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The certificate belongs to another person", "signature.cieSign.signerMismatch");
        }
        String revocation = revocationChecker.check(certificate, issuer);
        if (CieRevocationChecker.REVOKED.equals(revocation)) throw untrusted("The certificate is revoked");
        return new Result(
            readable(certificate.getSubjectX500Principal()),
            readable(certificate.getIssuerX500Principal()),
            HexFormat.of().withUpperCase().formatHex(certificate.getSerialNumber().toByteArray()),
            taxCode,
            revocation
        );
    }

    private static SignerData signer(CieSignPdfInspector.SignedPart part) {
        SignerData data;
        try {
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(part.signedContent()), contentInfo(part.contents()));
            Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
            if (signers.size() != 1) return fail("The signature must have one signer");
            SignerInformation information = signers.iterator().next();
            if (WEAK_DIGESTS.contains(information.getDigestAlgOID())) return fail("The signature uses a weak digest algorithm");
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches((Selector<X509CertificateHolder>) information.getSID());
            if (matches.isEmpty()) return fail("The signature has no certificate");
            X509Certificate certificate = converter.getCertificate(matches.iterator().next());
            // The key only: the validity of the certificate is checked at the upload, with its path.
            if (!information.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate.getPublicKey()))) return fail("The signature is not valid");
            List<X509Certificate> certificates = new ArrayList<>();
            for (X509CertificateHolder holder : cms.getCertificates().getMatches(null)) certificates.add(converter.getCertificate(holder));
            data = new SignerData(certificate, certificates);
        } catch (CMSException | OperatorCreationException | CertificateException | IOException | RuntimeException exception) {
            if (exception instanceof RequestAlertException alert) throw alert;
            return fail("The signature is not valid");
        }
        return data;
    }

    /**
     * The CMS structure in the signature value, which the PDF pads with zeros up to the reserved size: anything else
     * after it makes the signature invalid.
     */
    private static ContentInfo contentInfo(byte[] contents) throws IOException {
        ByteArrayInputStream input = new ByteArrayInputStream(contents);
        ASN1Primitive object;
        try (ASN1InputStream reader = new ASN1InputStream(input)) {
            object = reader.readObject();
        }
        if (object == null) fail("The signature value is empty");
        for (byte value : input.readAllBytes()) {
            if (value != 0) fail("Unexpected data after the signature");
        }
        return ContentInfo.getInstance(object);
    }

    private static SignerData fail(String message) {
        throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, message, "signature.cieSign.invalidSignature");
    }

    /**
     * The issuer of the certificate on a path to a trust anchor, valid at the given time; revocation is checked apart.
     */
    private X509Certificate issuer(X509Certificate certificate, List<X509Certificate> pool, Instant at) {
        try {
            X509CertSelector selector = new X509CertSelector();
            selector.setCertificate(certificate);
            PKIXBuilderParameters parameters = new PKIXBuilderParameters(anchors, selector);
            parameters.setRevocationEnabled(false);
            parameters.setDate(Date.from(at));
            parameters.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(pool)));
            PKIXCertPathBuilderResult result = (PKIXCertPathBuilderResult) CertPathBuilder.getInstance("PKIX").build(parameters);
            List<? extends Certificate> path = result.getCertPath().getCertificates();
            return path.size() > 1 ? (X509Certificate) path.get(1) : result.getTrustAnchor().getTrustedCert();
        } catch (GeneralSecurityException exception) {
            throw untrusted("The certificate is not issued by a CIE authority or is not valid");
        }
    }

    /**
     * Whether the holder of the certificate has the expected name, regardless of case, accents, spaces, apostrophes,
     * hyphens and of the order of first and last name.
     */
    static boolean sameName(String expected, X500Name subject) {
        String target = normalizeName(expected);
        if (target.isEmpty()) return false;
        String given = String.join(" ", values(subject, BCStyle.GIVENNAME));
        String surname = String.join(" ", values(subject, BCStyle.SURNAME));
        if (!given.isBlank() && !surname.isBlank()) {
            return target.equals(normalizeName(given + surname)) || target.equals(normalizeName(surname + given));
        }
        return values(subject, BCStyle.CN).stream().anyMatch(name -> target.equals(normalizeName(name)));
    }

    static String normalizeName(String value) {
        if (value == null) return "";
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return NAME_SEPARATORS.matcher(withoutAccents).replaceAll("").toUpperCase(Locale.ROOT);
    }

    /**
     * The Italian tax code of the holder, from the serial number (for example {@code TINIT-...}), the common name or the
     * qualifier of the subject.
     */
    static String taxCode(X500Name subject) {
        for (ASN1ObjectIdentifier attribute : List.of(BCStyle.SERIALNUMBER, BCStyle.CN, BCStyle.DN_QUALIFIER)) {
            for (String value : values(subject, attribute)) {
                Matcher matcher = TAX_CODE.matcher(value.toUpperCase(Locale.ROOT));
                while (matcher.find()) {
                    if (ItalianTaxCode.valid(matcher.group())) return matcher.group();
                }
            }
        }
        return null;
    }

    private static List<String> values(X500Name name, ASN1ObjectIdentifier attribute) {
        List<String> values = new ArrayList<>();
        for (RDN rdn : name.getRDNs(attribute)) {
            for (org.bouncycastle.asn1.x500.AttributeTypeAndValue value : rdn.getTypesAndValues()) {
                if (!value.getType().equals(attribute)) continue;
                ASN1Encodable content = value.getValue();
                if (content instanceof ASN1String string) values.add(string.getString());
            }
        }
        return values;
    }

    /**
     * A distinguished name with the attribute names, for example {@code SERIALNUMBER=TINIT-...}, instead of the object
     * identifiers that RFC 2253 uses for the attributes of the CIE.
     */
    private static String readable(X500Principal principal) {
        return X500Name.getInstance(BCStyle.INSTANCE, principal.getEncoded()).toString();
    }

    private static InputStream open(String location) throws IOException {
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            return new DefaultResourceLoader().getResource(location).getInputStream();
        }
        return Files.newInputStream(Path.of(location));
    }

    private static RequestAlertException untrusted(String message) {
        return SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, message, "signature.cieSign.untrustedCertificate");
    }

    static RequestAlertException notConfigured() {
        return SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The certificates of the CIE are not configured", "signature.cieSign.notConfigured");
    }

    /**
     * Uses the given certificates as trust anchors instead of the configured ones; for tests.
     */
    void useTrustAnchors(List<X509Certificate> certificates) {
        Set<TrustAnchor> value = new LinkedHashSet<>();
        certificates.forEach(certificate -> value.add(new TrustAnchor(certificate, null)));
        anchors = Set.copyOf(value);
    }

    /**
     * Who is expected to sign: the full name and the tax code of the holder.
     */
    public record Identity(String name, String taxCode) {
        @Override
        public String toString() {
            return "Identity[...]";
        }
    }

    /**
     * Data of the certificate recorded in the evidence, with the outcome of the revocation check.
     */
    public record Result(String subject, String issuer, String serial, String taxCode, String revocationCheck) {
        @Override
        public String toString() {
            return "Result[revocationCheck=" + revocationCheck + "]";
        }
    }

    private record SignerData(X509Certificate certificate, List<X509Certificate> certificates) {
    }
}
