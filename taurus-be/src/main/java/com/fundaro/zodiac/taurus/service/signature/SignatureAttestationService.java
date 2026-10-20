package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetPdfRenderer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Attestation of a signed act: a separate PDF, sealed, that lists the documents and the evidence of every signature.
 * It is separate from the documents because adding pages after a CieSign signature would invalidate it, and it holds
 * everything needed to verify the act after Taurus has removed its data.
 */
@Service
public class SignatureAttestationService {

    private static final String TEMPLATE = "templates/signature/attestation.html";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LoanSheetPdfRenderer renderer;
    private final SignatureSealService sealService;
    private final String template;

    public SignatureAttestationService(LoanSheetPdfRenderer renderer, SignatureSealService sealService) {
        this.renderer = renderer;
        this.sealService = sealService;
        this.template = read(TEMPLATE);
    }

    /**
     * Renders and seals the attestation of an act.
     */
    public byte[] create(Attestation attestation) {
        byte[] pdf = renderer.render(template, model(attestation, sealService.certificateFingerprint()), false);
        return sealService.seal(pdf, "Attestazione di firma elettronica");
    }

    static Map<String, Object> model(Attestation attestation, String sealFingerprint) {
        ZoneId zone = attestation.zone();
        Map<String, Object> act = new LinkedHashMap<>();
        act.put("type", SignatureLabels.act(attestation.actType()));
        act.put("sheetNumber", attestation.sheetNumber());
        act.put("id", String.valueOf(attestation.actId()));
        act.put("completedAtLocal", LOCAL.format(attestation.completedAt().withZoneSameInstant(zone)));
        act.put("completedAtUtc", UTC.format(attestation.completedAt().withZoneSameInstant(ZoneOffset.UTC)));
        List<Map<String, Object>> documents = attestation.documents().stream().map(document -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("role", SignatureLabels.document(document.role()));
            value.put("frozenHash", document.frozenHash());
            value.put("sealedHash", document.sealedHash());
            return value;
        }).toList();
        List<Map<String, Object>> signatures = attestation.signatures().stream().map(signature -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", signature.name());
            value.put("role", SignatureLabels.signer(signature.role()));
            value.put("purpose", SignatureLabels.purpose(signature.purpose()));
            value.put("document", SignatureLabels.document(signature.document()));
            value.put("declaration", signature.declaration());
            value.put("method", SignatureLabels.method(signature.method()));
            value.put("signedAtLocal", LOCAL.format(signature.signedAt().withZoneSameInstant(zone)));
            value.put("signedAtUtc", UTC.format(signature.signedAt().withZoneSameInstant(ZoneOffset.UTC)));
            value.put("maskedEmail", SignatureSupport.maskEmail(signature.email()));
            value.put("certificateSubject", signature.certificateSubject());
            value.put("certificateIssuer", signature.certificateIssuer());
            value.put("certificateSerial", signature.certificateSerial());
            value.put("certificateTaxCode", signature.certificateTaxCode());
            value.put("revocationCheck", revocation(signature.revocationCheck()));
            value.put("ipAddress", signature.ipAddress());
            value.put("userAgent", signature.userAgent());
            value.put("sequence", String.valueOf(signature.sequence()));
            value.put("evidenceHash", signature.evidenceHash());
            return value;
        }).toList();
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("tenant", Map.of("name", attestation.tenantName()));
        model.put("act", act);
        model.put("documents", documents);
        model.put("signatures", signatures);
        model.put("seal", Map.of("fingerprint", sealFingerprint));
        return model;
    }

    private static String revocation(String value) {
        if (value == null) return null;
        return switch (value) {
            case "GOOD" -> "certificato non revocato";
            case "UNAVAILABLE" -> "elenco di revoca non raggiungibile";
            default -> value;
        };
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }

    public record Attestation(
        String tenantName,
        SignatureActType actType,
        String sheetNumber,
        Long actId,
        ZonedDateTime completedAt,
        ZoneId zone,
        List<Document> documents,
        List<Signature> signatures
    ) {
    }

    public record Document(SignatureDocumentRole role, String frozenHash, String sealedHash) {
    }

    public record Signature(
        String name,
        SignerRole role,
        SignaturePurpose purpose,
        SignatureDocumentRole document,
        String declaration,
        SignatureEvidenceMethod method,
        ZonedDateTime signedAt,
        String email,
        String certificateSubject,
        String certificateIssuer,
        String certificateSerial,
        String certificateTaxCode,
        String revocationCheck,
        String ipAddress,
        String userAgent,
        long sequence,
        String evidenceHash
    ) {
    }
}
