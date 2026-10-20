package com.fundaro.zodiac.taurus.service.signature;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * A test certification authority shaped like the one of the CIE, and PAdES signatures made with its certificates, for
 * the tests of the CieSign verification.
 */
final class CieSignTestSupport {

    static final String TAX_CODE = taxCode("RSSMRA80A01H501");
    static final String OTHER_TAX_CODE = taxCode("BNCLRA85M41F205");

    private static final SecureRandom RANDOM = new SecureRandom();

    private CieSignTestSupport() {
    }

    /**
     * A key with its certificate.
     */
    record Issued(PrivateKey key, X509Certificate certificate) {
    }

    /**
     * Root and intermediate authority, as the CIE has.
     */
    record Authority(Issued root, Issued intermediate) {
        /**
         * A certificate of a card holder, with the tax code in the serial number like the CIE.
         */
        Issued holder(String givenName, String surname, String taxCode, String crlLocation) {
            return holder(givenName, surname, taxCode, crlLocation, Instant.now().plus(365, ChronoUnit.DAYS));
        }

        Issued holder(String givenName, String surname, String taxCode, String crlLocation, Instant notAfter) {
            X500Name subject = new X500NameBuilder(BCStyle.INSTANCE)
                .addRDN(BCStyle.C, "IT")
                .addRDN(BCStyle.SURNAME, surname)
                .addRDN(BCStyle.GIVENNAME, givenName)
                .addRDN(BCStyle.SERIALNUMBER, "TINIT-" + taxCode)
                .addRDN(BCStyle.CN, taxCode + "/7420091000049623")
                .build();
            return issue(subject, intermediate, false, notAfter, crlLocation);
        }

        List<X509Certificate> chain(Issued holder) {
            return List.of(holder.certificate(), intermediate.certificate());
        }
    }

    static Authority authority(String name) {
        Issued root = issue(new X500Name("CN=" + name + " Root,O=Test,C=IT"), null, true, Instant.now().plus(3650, ChronoUnit.DAYS), null);
        Issued intermediate = issue(new X500Name("CN=" + name + " Sub CA,O=Test,C=IT"), root, true, Instant.now().plus(1825, ChronoUnit.DAYS), null);
        return new Authority(root, intermediate);
    }

    private static Issued issue(X500Name subject, Issued issuer, boolean authority, Instant notAfter, String crlLocation) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            X500Name issuerName = issuer == null ? subject : X500Name.getInstance(issuer.certificate().getSubjectX500Principal().getEncoded());
            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerName,
                new BigInteger(64, RANDOM).abs().add(BigInteger.ONE),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(notAfter),
                subject,
                pair.getPublic()
            );
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(authority));
            builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(authority ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature | KeyUsage.nonRepudiation)
            );
            if (crlLocation != null) {
                DistributionPointName name = new DistributionPointName(new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, crlLocation)));
                builder.addExtension(Extension.cRLDistributionPoints, false, new CRLDistPoint(new DistributionPoint[] { new DistributionPoint(name, null, null) }));
            }
            PrivateKey signingKey = issuer == null ? pair.getPrivate() : issuer.key();
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signingKey)));
            return new Issued(pair.getPrivate(), certificate);
        } catch (GeneralSecurityException | IOException | org.bouncycastle.operator.OperatorCreationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * A revocation list of the intermediate authority with the given certificates revoked.
     */
    static X509CRL crl(Authority authority, X509Certificate... revoked) {
        try {
            Date now = new Date();
            X509v2CRLBuilder builder = new X509v2CRLBuilder(new X509CertificateHolder(authority.intermediate().certificate().getEncoded()).getSubject(), now);
            builder.setNextUpdate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
            for (X509Certificate certificate : revoked) builder.addCRLEntry(certificate.getSerialNumber(), now, CRLReason.keyCompromise);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(authority.intermediate().key());
            return new JcaX509CRLConverter().getCRL(builder.build(signer));
        } catch (GeneralSecurityException | IOException | org.bouncycastle.operator.OperatorCreationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * A simple frozen document of two pages.
     */
    static byte[] frozenPdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : List.of("Foglio di affidamento n. 2026/007", "Clausole")) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Signs a PDF as CieSign does: a PAdES signature appended as an incremental update.
     */
    static byte[] sign(byte[] pdf, Issued signer, List<X509Certificate> chain) {
        return sign(pdf, signer, chain, document -> {});
    }

    /**
     * Signs a PDF after changing the document in the same incremental update.
     */
    static byte[] sign(byte[] pdf, Issued signer, List<X509Certificate> chain, Consumer<PDDocument> change) {
        try (PDDocument document = Loader.loadPDF(pdf); SignatureOptions options = new SignatureOptions(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            change.accept(document);
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ETSI_CADES_DETACHED);
            signature.setName("Firma CIE");
            signature.setSignDate(Calendar.getInstance());
            options.setPreferredSignatureSize(16 * 1024);
            document.addSignature(signature, content -> cms(content, signer, chain), options);
            document.saveIncremental(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static byte[] cms(InputStream content, Issued signer, List<X509Certificate> chain) throws IOException {
        try {
            ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(signer.key());
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(
                new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(contentSigner, signer.certificate())
            );
            generator.addCertificates(new JcaCertStore(new ArrayList<>(chain)));
            return generator.generate(new CMSProcessableByteArray(content.readAllBytes()), false).getEncoded("DER");
        } catch (GeneralSecurityException | org.bouncycastle.operator.OperatorCreationException | org.bouncycastle.cms.CMSException exception) {
            throw new IOException(exception);
        }
    }

    /**
     * A valid tax code with the given first fifteen characters.
     */
    static String taxCode(String prefix) {
        for (char check = 'A'; check <= 'Z'; check++) {
            if (ItalianTaxCode.valid(prefix + check)) return prefix + check;
        }
        throw new IllegalArgumentException(prefix);
    }
}
