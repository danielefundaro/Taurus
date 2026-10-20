package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetActs;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.HolderData;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContentFactory;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetModelFactory;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetPdfRenderer;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetRegisterBuilder;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetText;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Documents of the acts of a loan sheet: each one is generated once from the template the sheet was issued with,
 * stored and identified by its SHA-256 hash. A frozen document is never generated again.
 */
@Component
public class LoanSheetActDocuments {

    static final String MEDIA_CATEGORY = "signature-acts";
    private static final String CLAUSES_TEMPLATE = "templates/signature/clauses-approval.html";
    private static final String PDF = "application/pdf";

    private final LoanSheetContentFactory contentFactory;
    private final LoanSheetPdfRenderer renderer;
    private final MediaService mediaService;
    private final SignatureDocumentRepository documentRepository;
    private final int graceDays;
    private final String clausesTemplate;

    public LoanSheetActDocuments(
        LoanSheetContentFactory contentFactory,
        LoanSheetPdfRenderer renderer,
        MediaService mediaService,
        SignatureDocumentRepository documentRepository,
        ApplicationProperties applicationProperties
    ) {
        this.contentFactory = contentFactory;
        this.renderer = renderer;
        this.mediaService = mediaService;
        this.documentRepository = documentRepository;
        this.graceDays = applicationProperties.getSignature().getDownloadGraceDays();
        this.clausesTemplate = read(CLAUSES_TEMPLATE);
    }

    /**
     * Generates, stores and records the documents of an act: the sheet and the approval of the clauses for the issue,
     * the rows of the operation for a variation, the return report for the return. {@code holder} is the data entered
     * for the issue.
     */
    public List<SignatureDocument> freeze(
        SignatureAct act,
        InventoryLoanSheet sheet,
        HolderData holder,
        String tenantCode,
        String actor
    ) {
        String template = sheet.getTemplate().getContent();
        String name = LoanSheetActs.ISSUE;
        Set<String> registerKeys = null;
        if (act.getActType() == SignatureActType.LOAN_SHEET_VARIATION) {
            name = LoanSheetActs.VARIATION;
            registerKeys = Set.of(registerKey(act));
        } else if (act.getActType() == SignatureActType.LOAN_SHEET_RETURN) {
            name = LoanSheetActs.RETURN;
        }
        LoanSheetContent content = contentFactory.actContent(
            sheet,
            template,
            new LoanSheetContent.Signature(name, holder, graceDays),
            registerKeys,
            tenantCode
        );
        Map<String, Object> model = LoanSheetModelFactory.build(content);
        List<SignatureDocument> frozen = new ArrayList<>();
        SignatureDocument main = store(act, SignatureDocumentRole.MAIN, renderer.renderAct(template, model, name), fileName(sheet, act, "foglio"), tenantCode, actor);
        frozen.add(main);
        if (act.getActType() == SignatureActType.LOAN_SHEET_ISSUE) {
            String clauses = LoanSheetActs.clausesText(renderer.html(template, model));
            if (clauses == null) {
                throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The template has no block of clauses", "inventory.loanSheet.templateNotSignable");
            }
            Map<String, Object> clausesModel = new LinkedHashMap<>();
            clausesModel.put("tenant", Map.of("name", text(content.tenant().name())));
            clausesModel.put("sheet", Map.of("number", text(sheet.getSheetNumber())));
            clausesModel.put("holder", Map.of("name", text(holderName(sheet, holder))));
            clausesModel.put("main", Map.of("hash", main.getFrozenHash()));
            clausesModel.put("clauses", text(clauses));
            frozen.add(store(act, SignatureDocumentRole.CLAUSES, renderer.render(clausesTemplate, clausesModel, false), fileName(sheet, act, "clausole"), tenantCode, actor));
        }
        return frozen;
    }

    /**
     * Stores the file the holder signed with CieSign: the frozen document followed by the signature, sealed instead of
     * the frozen document when the act is complete.
     */
    public void storeSigned(SignatureDocument document, byte[] signed, InventoryLoanSheet sheet, SignatureAct act, String tenantCode, String actor) {
        MediaDTO media = mediaService.store(
            signed,
            fileName(sheet, act, document.getRole() == SignatureDocumentRole.CLAUSES ? "clausole-ciesign" : "ciesign"),
            PDF,
            MEDIA_CATEGORY,
            tenantCode,
            actor
        );
        document.setSignedMediaAssetId(media.getId());
        document.touchAudit(actor);
        documentRepository.save(document);
    }

    /**
     * The file to seal: the one signed with CieSign, when present, or the frozen document.
     */
    public byte[] toSeal(SignatureDocument document, String tenantCode) {
        return read(document.getSignedMediaAssetId() != null ? document.getSignedMediaAssetId() : document.getFrozenMediaAssetId(), tenantCode);
    }

    /**
     * Stores the sealed copy of a document.
     */
    public void storeSealed(SignatureDocument document, byte[] sealed, InventoryLoanSheet sheet, SignatureAct act, String tenantCode, String actor) {
        MediaDTO media = mediaService.store(
            sealed,
            fileName(sheet, act, document.getRole() == SignatureDocumentRole.CLAUSES ? "clausole-firmato" : "firmato"),
            PDF,
            MEDIA_CATEGORY,
            tenantCode,
            actor
        );
        document.setSealedMediaAssetId(media.getId());
        document.setSealedHash(SignatureSupport.sha256(sealed));
        document.touchAudit(actor);
        documentRepository.save(document);
    }

    /**
     * Stores the attestation of an act and returns the identifier of its media.
     */
    public Long storeAttestation(byte[] attestation, InventoryLoanSheet sheet, SignatureAct act, String tenantCode, String actor) {
        return mediaService.store(attestation, fileName(sheet, act, "attestazione"), PDF, MEDIA_CATEGORY, tenantCode, actor).getId();
    }

    public byte[] read(Long mediaId, String tenantCode) {
        return mediaService.getContent(mediaId, tenantCode).bytes();
    }

    /**
     * Discards documents not signed by anyone, replaced by new ones: they are marked as deleted and their files removed.
     */
    public void discard(List<SignatureDocument> discarded, String tenantCode, String actor) {
        for (SignatureDocument document : discarded) {
            document.setDeleted(true);
            document.touchAudit(actor);
            documentRepository.saveAndFlush(document);
            deleteMedia(document.getFrozenMediaAssetId(), tenantCode, actor);
        }
    }

    /**
     * Removes the files of a document and keeps its hashes, which the evidence and the attestation refer to.
     */
    public void removeFiles(SignatureDocument document, String tenantCode, String actor) {
        List<Long> media = new ArrayList<>();
        if (document.getFrozenMediaAssetId() != null) media.add(document.getFrozenMediaAssetId());
        if (document.getSignedMediaAssetId() != null) media.add(document.getSignedMediaAssetId());
        if (document.getSealedMediaAssetId() != null) media.add(document.getSealedMediaAssetId());
        if (media.isEmpty()) return;
        document.setFrozenMediaAssetId(null);
        document.setSignedMediaAssetId(null);
        document.setSealedMediaAssetId(null);
        document.touchAudit(actor);
        documentRepository.saveAndFlush(document);
        media.forEach(id -> deleteMedia(id, tenantCode, actor));
    }

    public void deleteMedia(Long mediaId, String tenantCode, String actor) {
        if (mediaId != null) mediaService.deleteIfUnreferenced(mediaId, tenantCode, actor);
    }

    static String registerKey(SignatureAct act) {
        if (act.getAssignmentId() != null) return LoanSheetRegisterBuilder.assignmentKey(act.getAssignmentId());
        if (act.getRenewalId() != null) return LoanSheetRegisterBuilder.renewalKey(act.getRenewalId());
        return LoanSheetRegisterBuilder.returnKey(act.getReturnId());
    }

    /**
     * File name of a document of an act, for example {@code foglio-2026-007-emissione-foglio.pdf}.
     */
    static String fileName(InventoryLoanSheet sheet, SignatureAct act, String suffix) {
        String type = switch (act.getActType()) {
            case LOAN_SHEET_ISSUE -> "emissione";
            case LOAN_SHEET_VARIATION -> "variazione-" + act.getId();
            case LOAN_SHEET_RETURN -> "riconsegna";
            case ASSIGNMENT_ACKNOWLEDGMENT -> "presa-visione-" + act.getId();
        };
        return "foglio-" + sheet.getSheetNumber().replace('/', '-') + "-" + type + "-" + suffix + ".pdf";
    }

    private SignatureDocument store(
        SignatureAct act,
        SignatureDocumentRole role,
        byte[] pdf,
        String fileName,
        String tenantCode,
        String actor
    ) {
        if (pdf.length > LoanSheetPdfRenderer.MAX_PDF_BYTES) {
            throw SignatureSupport.error(HttpStatus.PAYLOAD_TOO_LARGE, "The document of the act exceeds 20 MB", "inventory.loanSheet.tooLarge")
                .with("maxMegabytes", (int) (LoanSheetPdfRenderer.MAX_PDF_BYTES / (1024 * 1024)));
        }
        MediaDTO media = mediaService.store(pdf, fileName, PDF, MEDIA_CATEGORY, tenantCode, actor);
        SignatureDocument document = new SignatureDocument();
        document.initializeAudit(actor);
        document.setActId(act.getId());
        document.setRole(role);
        document.setFrozenMediaAssetId(media.getId());
        document.setFrozenHash(SignatureSupport.sha256(pdf));
        return documentRepository.save(document);
    }

    /**
     * Who approves the clauses: the adult member or, once entered, the parent or guardian.
     */
    private static String holderName(InventoryLoanSheet sheet, HolderData holder) {
        if (holder != null && holder.holderName() != null) return holder.holderName();
        return LoanSheetText.join(" ", sheet.getMemberName(), sheet.getMemberLastName());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String read(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read " + path, exception);
        }
    }
}
