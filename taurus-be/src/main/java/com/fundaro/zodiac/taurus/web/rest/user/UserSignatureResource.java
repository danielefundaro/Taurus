package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.*;
import com.fundaro.zodiac.taurus.service.signature.CieSignService;
import com.fundaro.zodiac.taurus.service.signature.LoanSheetSignatureService;
import com.fundaro.zodiac.taurus.service.signature.SignatureChallengeService;
import com.fundaro.zodiac.taurus.web.rest.RequiresTenantFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Electronic signature of the authenticated user: available methods, confirmations by code, documents signed with
 * CieSign and the acts of the loan sheets the user signs.
 */
@RestController
@RequestMapping("/api/user/signatures")
@RequiresTenantFeature(TenantFeature.INVENTORY_SIGNATURE)
public class UserSignatureResource {

    private final SignatureChallengeService challengeService;
    private final LoanSheetSignatureService loanSheetSignatureService;
    private final CieSignService cieSignService;

    public UserSignatureResource(
        SignatureChallengeService challengeService,
        LoanSheetSignatureService loanSheetSignatureService,
        CieSignService cieSignService
    ) {
        this.challengeService = challengeService;
        this.loanSheetSignatureService = loanSheetSignatureService;
        this.cieSignService = cieSignService;
    }

    @GetMapping("/methods")
    public Methods getMethods(AbstractAuthenticationToken token) {
        return challengeService.methods(token);
    }

    /**
     * Saves the preferred method; the authentication app is proposed only once configured.
     */
    @PutMapping("/methods")
    public Methods putMethods(@Valid @RequestBody MethodPreference request, AbstractAuthenticationToken token) {
        return challengeService.updatePreference(request.preference(), token);
    }

    @PostMapping("/challenges")
    public ResponseEntity<Challenge> createChallenge(
        @Valid @RequestBody ChallengeRequest request,
        HttpServletRequest httpRequest,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.status(201).body(challengeService.create(request, httpRequest, token));
    }

    @PostMapping("/challenges/{id}/resend")
    public Challenge resend(@PathVariable long id, AbstractAuthenticationToken token) {
        return challengeService.resend(id, token);
    }

    @PostMapping("/challenges/{id}/confirm")
    public Confirmation confirm(
        @PathVariable long id,
        @Valid @RequestBody ConfirmRequest request,
        HttpServletRequest httpRequest,
        AbstractAuthenticationToken token
    ) {
        return challengeService.confirm(id, request, httpRequest, token);
    }

    @GetMapping("/acts")
    public List<Act> getActs(AbstractAuthenticationToken token) {
        return loanSheetSignatureService.ownActs(token);
    }

    @GetMapping("/acts/{id}")
    public Act getAct(@PathVariable long id, AbstractAuthenticationToken token) {
        return loanSheetSignatureService.ownAct(id, token);
    }

    @PutMapping("/acts/{id}/holder-data")
    public Act putHolderData(@PathVariable long id, @Valid @RequestBody HolderDataRequest request, AbstractAuthenticationToken token) {
        return loanSheetSignatureService.submitHolderData(id, request, token);
    }

    @GetMapping(value = "/acts/{id}/documents/{role}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getDocument(@PathVariable long id, @PathVariable SignatureDocumentRole role, AbstractAuthenticationToken token) {
        return file(loanSheetSignatureService.document(id, role, false, token), MediaType.APPLICATION_PDF);
    }

    @GetMapping(value = "/acts/{id}/attestation", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getAttestation(@PathVariable long id, AbstractAuthenticationToken token) {
        return file(loanSheetSignatureService.attestation(id, false, token), MediaType.APPLICATION_PDF);
    }

    /**
     * A document signed with CieSign by the holder, verified and recorded as the signature of the holder.
     */
    @PostMapping(value = "/acts/{id}/documents/{role}/ciesign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ActOutcome postCieSign(
        @PathVariable long id,
        @PathVariable SignatureDocumentRole role,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest httpRequest,
        AbstractAuthenticationToken token
    ) {
        return cieSignService.sign(id, role, file, httpRequest, token);
    }

    public static ResponseEntity<byte[]> file(ActFile file, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(file.bytes());
    }
}
