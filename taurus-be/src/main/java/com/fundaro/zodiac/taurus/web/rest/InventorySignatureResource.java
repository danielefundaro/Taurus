package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Act;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.AssociationSignerChange;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.AssociationSignerOption;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChainVerification;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Challenge;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Evidence;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.PaperDeclarationRequest;
import com.fundaro.zodiac.taurus.service.signature.LoanSheetSignatureService;
import com.fundaro.zodiac.taurus.service.signature.SignatureChallengeService;
import com.fundaro.zodiac.taurus.service.signature.SignatureEvidenceService;
import com.fundaro.zodiac.taurus.service.signature.SignatureLinkAdministration;
import com.fundaro.zodiac.taurus.web.rest.user.UserSignatureResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administration of the electronic signature of the tenant: acts of the loan sheets, their evidence, documents and
 * dossier, and the verification of the evidence chain.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiresTenantFeature(TenantFeature.INVENTORY)
public class InventorySignatureResource {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final SignatureEvidenceService evidenceService;
    private final LoanSheetSignatureService loanSheetSignatureService;
    private final SignatureChallengeService challengeService;
    private final SignatureLinkAdministration linkAdministration;

    public InventorySignatureResource(
        SignatureEvidenceService evidenceService,
        LoanSheetSignatureService loanSheetSignatureService,
        SignatureChallengeService challengeService,
        SignatureLinkAdministration linkAdministration
    ) {
        this.evidenceService = evidenceService;
        this.loanSheetSignatureService = loanSheetSignatureService;
        this.challengeService = challengeService;
        this.linkAdministration = linkAdministration;
    }

    @PostMapping("/signatures/verify-chain")
    public ChainVerification verifyChain(AbstractAuthenticationToken token) {
        return evidenceService.verifyChain(token);
    }

    @GetMapping("/users/{userIndex}/association-signers")
    public List<AssociationSignerOption> getAssociationSigners(@PathVariable Long userIndex) {
        return loanSheetSignatureService.associationSignersForMember(userIndex);
    }

    @GetMapping("/loan-sheets/{id}/signature-acts")
    public List<Act> getSheetActs(@PathVariable long id, AbstractAuthenticationToken token) {
        return loanSheetSignatureService.sheetActs(id, token);
    }

    @GetMapping(value = "/loan-sheets/{id}/signature-dossier", produces = "application/zip")
    public ResponseEntity<byte[]> getDossier(@PathVariable long id, AbstractAuthenticationToken token) {
        return UserSignatureResource.file(loanSheetSignatureService.dossier(id, token), ZIP);
    }

    @GetMapping("/signature-acts/{id}/evidence")
    public List<Evidence> getEvidence(@PathVariable long id, AbstractAuthenticationToken token) {
        return loanSheetSignatureService.evidence(id, token);
    }

    @GetMapping(value = "/signature-acts/{id}/documents/{role}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getDocument(@PathVariable long id, @PathVariable SignatureDocumentRole role, AbstractAuthenticationToken token) {
        return UserSignatureResource.file(loanSheetSignatureService.document(id, role, true, token), MediaType.APPLICATION_PDF);
    }

    @GetMapping(value = "/signature-acts/{id}/attestation", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getAttestation(@PathVariable long id, AbstractAuthenticationToken token) {
        return UserSignatureResource.file(loanSheetSignatureService.attestation(id, true, token), MediaType.APPLICATION_PDF);
    }

    @PutMapping("/signature-acts/{id}/association-signer")
    public Act putAssociationSigner(@PathVariable long id, @Valid @RequestBody AssociationSignerChange request, AbstractAuthenticationToken token) {
        return loanSheetSignatureService.changeAssociationSigner(id, request, token);
    }

    /**
     * Sends a new link to a parent or guardian without an account, revoking the previous one.
     */
    @PostMapping("/signature-acts/{id}/links/{signerId}/resend")
    public Act resendLink(@PathVariable long id, @PathVariable long signerId, AbstractAuthenticationToken token) {
        return linkAdministration.resend(id, signerId, token);
    }

    @DeleteMapping("/signature-acts/{id}/links/{signerId}")
    public Act revokeLink(@PathVariable long id, @PathVariable long signerId, AbstractAuthenticationToken token) {
        return linkAdministration.revoke(id, signerId, token);
    }

    /**
     * Opens the confirmation of a declaration of paper signature: the administrator confirms it with the own code, at
     * {@code /api/user/signatures/challenges/{id}/confirm}.
     */
    @PostMapping("/signature-acts/{id}/paper-declaration")
    public ResponseEntity<Challenge> postPaperDeclaration(
        @PathVariable long id,
        @Valid @RequestBody PaperDeclarationRequest request,
        HttpServletRequest httpRequest,
        AbstractAuthenticationToken token
    ) {
        SignatureMethod method = request.method() == null ? SignatureMethod.EMAIL_OTP : request.method();
        ChallengeRequest challenge = new ChallengeRequest(SignaturePurpose.PAPER_DECLARATION, method, List.of(ChallengeItem.paper(id, request.note())));
        return ResponseEntity.status(201).body(challengeService.create(challenge, httpRequest, token));
    }
}
