package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Who signs, with the request data recorded in the evidence: the authenticated user or the parent or guardian of a
 * signature link.
 */
@Component
public class SignatureSigners {

    private static final int USER_AGENT_LENGTH = 512;

    private final UsersRepository usersRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureLinkService linkService;

    public SignatureSigners(UsersRepository usersRepository, SignatureActSignerRepository signerRepository, SignatureLinkService linkService) {
        this.usersRepository = usersRepository;
        this.signerRepository = signerRepository;
        this.linkService = linkService;
    }

    /**
     * The authenticated user, identified by the subject of the token.
     */
    public Signer user(String userId, AbstractAuthenticationToken token, HttpServletRequest httpRequest, String tenant) {
        Users user = usersRepository.findByKeycloakIdAndDeletedFalse(userId).orElse(null);
        String firstName = user != null ? user.getName() : SecurityUtils.getFirstNameFromAuthentication(token);
        String lastName = user != null ? user.getLastName() : SecurityUtils.getLastNameFromAuthentication(token);
        String displayName = (Objects.requireNonNullElse(firstName, "") + " " + Objects.requireNonNullElse(lastName, "")).trim();
        String sessionId = token instanceof JwtAuthenticationToken jwt ? jwt.getToken().getClaimAsString("sid") : null;
        return new Signer(
            userId,
            null,
            userId,
            displayName.isBlank() ? userId : displayName,
            user == null ? null : SignatureSupport.trimToNull(user.getEmail()),
            sessionId,
            httpRequest == null ? null : httpRequest.getRemoteAddr(),
            userAgent(httpRequest),
            tenant,
            token
        );
    }

    /**
     * The parent or guardian of a signature link, read again with the link checked in the current transaction.
     */
    public Signer link(SignatureLinkSession session, HttpServletRequest httpRequest) {
        linkService.requireActive(session);
        SignatureActSigner signer = signerRepository.findById(session.signerId())
            .filter(value -> !value.isDeleted() && value.getUserKeycloakId() == null)
            .orElseThrow(SignatureLinkUnavailableException::new);
        return new Signer(
            null,
            signer.getId(),
            session.actor(),
            signer.getDisplayName(),
            SignatureSupport.trimToNull(signer.getEmail()),
            "link:" + session.fingerprint(),
            httpRequest == null ? null : httpRequest.getRemoteAddr(),
            userAgent(httpRequest),
            session.tenantCode(),
            null
        );
    }

    private static String userAgent(HttpServletRequest httpRequest) {
        String userAgent = httpRequest == null ? null : SignatureSupport.trimToNull(httpRequest.getHeader("User-Agent"));
        return userAgent != null && userAgent.length() > USER_AGENT_LENGTH ? userAgent.substring(0, USER_AGENT_LENGTH) : userAgent;
    }
}
