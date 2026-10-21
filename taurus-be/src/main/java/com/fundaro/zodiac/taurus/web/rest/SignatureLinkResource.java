package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActFile;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Challenge;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Confirmation;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ConfirmRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.HolderDataRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.LinkAct;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.LinkChallengeRequest;
import com.fundaro.zodiac.taurus.service.signature.CieSignService;
import com.fundaro.zodiac.taurus.service.signature.LoanSheetSignatureService;
import com.fundaro.zodiac.taurus.service.signature.SignatureChallengeService;
import com.fundaro.zodiac.taurus.service.signature.SignatureLinkRateLimitException;
import com.fundaro.zodiac.taurus.service.signature.SignatureLinkResolver;
import com.fundaro.zodiac.taurus.service.signature.SignatureLinkUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public signature page of a parent or guardian without an account: the token of the link is the only credential and
 * resolves tenant, act and signer. An unknown, expired or revoked token, an inactive tenant or the inventory disabled
 * answer {@code 404} without a body. Responses are never cached and never send the address as referrer.
 */
@RestController
@RequestMapping("/api/signature-links/v1/{token}")
public class SignatureLinkResource {

    private final SignatureLinkResolver resolver;
    private final LoanSheetSignatureService loanSheetSignatureService;
    private final SignatureChallengeService challengeService;
    private final CieSignService cieSignService;

    public SignatureLinkResource(
        SignatureLinkResolver resolver,
        LoanSheetSignatureService loanSheetSignatureService,
        SignatureChallengeService challengeService,
        CieSignService cieSignService
    ) {
        this.resolver = resolver;
        this.loanSheetSignatureService = loanSheetSignatureService;
        this.challengeService = challengeService;
        this.cieSignService = cieSignService;
    }

    @GetMapping
    public ResponseEntity<LinkAct> getAct(@PathVariable String token, HttpServletRequest request) {
        return json(HttpStatus.OK, resolver.within(token, request.getRemoteAddr(), loanSheetSignatureService::linkAct));
    }

    @PutMapping("/holder-data")
    public ResponseEntity<LinkAct> putHolderData(@PathVariable String token, @Valid @RequestBody HolderDataRequest body, HttpServletRequest request) {
        LinkAct act = resolver.within(token, request.getRemoteAddr(), session -> {
            loanSheetSignatureService.submitLinkHolderData(session, body);
            return loanSheetSignatureService.linkAct(session);
        });
        return json(HttpStatus.OK, act);
    }

    @GetMapping(value = "/documents/{role}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getDocument(@PathVariable String token, @PathVariable SignatureDocumentRole role, HttpServletRequest request) {
        return file(resolver.within(token, request.getRemoteAddr(), session -> loanSheetSignatureService.linkDocument(session, role)));
    }

    @GetMapping(value = "/attestation", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getAttestation(@PathVariable String token, HttpServletRequest request) {
        return file(resolver.within(token, request.getRemoteAddr(), loanSheetSignatureService::linkAttestation));
    }

    /**
     * A document of the act of the link signed with CieSign by the parent or guardian.
     */
    @PostMapping(value = "/documents/{role}/ciesign", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ActOutcome> postCieSign(
        @PathVariable String token,
        @PathVariable SignatureDocumentRole role,
        @RequestPart("file") MultipartFile file,
        HttpServletRequest request
    ) {
        return json(HttpStatus.OK, resolver.within(token, request.getRemoteAddr(), session -> cieSignService.signForLink(session, role, file, request)));
    }

    @PostMapping("/challenges")
    public ResponseEntity<Challenge> createChallenge(@PathVariable String token, @Valid @RequestBody LinkChallengeRequest body, HttpServletRequest request) {
        return json(HttpStatus.CREATED, resolver.within(token, request.getRemoteAddr(), session -> challengeService.createForLink(session, body, request)));
    }

    @PostMapping("/challenges/{id}/resend")
    public ResponseEntity<Challenge> resend(@PathVariable String token, @PathVariable long id, HttpServletRequest request) {
        return json(HttpStatus.OK, resolver.within(token, request.getRemoteAddr(), session -> challengeService.resendForLink(session, id)));
    }

    @PostMapping("/challenges/{id}/confirm")
    public ResponseEntity<Confirmation> confirm(
        @PathVariable String token,
        @PathVariable long id,
        @Valid @RequestBody ConfirmRequest body,
        HttpServletRequest request
    ) {
        return json(HttpStatus.OK, resolver.within(token, request.getRemoteAddr(), session -> challengeService.confirmForLink(session, id, body, request)));
    }

    @ExceptionHandler(SignatureLinkUnavailableException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).headers(privateHeaders()).build();
    }

    @ExceptionHandler(SignatureLinkRateLimitException.class)
    public ResponseEntity<Void> tooManyRequests() {
        HttpHeaders headers = privateHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "3600");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).build();
    }

    private static <T> ResponseEntity<T> json(HttpStatus status, T body) {
        return ResponseEntity.status(status).headers(privateHeaders()).body(body);
    }

    private static ResponseEntity<byte[]> file(ActFile file) {
        HttpHeaders headers = privateHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(file.bytes());
    }

    private static HttpHeaders privateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        headers.set("Referrer-Policy", "no-referrer");
        return headers;
    }
}
