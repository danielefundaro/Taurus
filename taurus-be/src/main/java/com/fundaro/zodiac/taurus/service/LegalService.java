package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import com.fundaro.zodiac.taurus.service.dto.PublicLegalDocumentDTO;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Legal documents of the platform: versions written by the super administrator, immutable from their publication,
 * read by anyone and confirmed by every user in each tenant.
 */
public interface LegalService {

    LegalStatusDTO getStatus(AbstractAuthenticationToken authenticationToken);

    LegalStatusDTO accept(LegalAcceptanceRequestDTO request, AbstractAuthenticationToken authenticationToken);

    /** Every version, drafts first, without the text. */
    List<LegalDocumentDTO> findAllDocuments();

    LegalDocumentDTO findDocument(Long id);

    /** Creates a draft. */
    LegalDocumentDTO createDocument(LegalDocumentDTO document);

    /** Changes a draft, or adds once the text of a document published with its URL only. */
    LegalDocumentDTO updateDocument(Long id, LegalDocumentDTO document);

    /** Deletes a draft. */
    void deleteDocument(Long id);

    /** Publishes a draft or reactivates a retired version, retiring the other version of the same document. */
    LegalDocumentDTO activateDocument(Long id);

    /** Retires the version in force. */
    LegalDocumentDTO withdrawDocument(Long id);

    /** HTML of a text as the public page shows it; empty for an empty text. */
    String preview(String content);

    /** The version in force of a document, by its public name ({@code terms} or {@code privacy}). */
    PublicLegalDocumentDTO findPublished(String type);

    /** A published version of a document, by its public name and version. */
    PublicLegalDocumentDTO findPublished(String type, String version);
}
