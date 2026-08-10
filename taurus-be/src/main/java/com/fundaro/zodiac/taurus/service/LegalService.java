package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public interface LegalService {

    LegalStatusDTO getStatus(AbstractAuthenticationToken authenticationToken);

    LegalStatusDTO accept(LegalAcceptanceRequestDTO request, AbstractAuthenticationToken authenticationToken);

    List<LegalDocumentDTO> findAllDocuments();

    LegalDocumentDTO createDocument(LegalDocumentDTO document);

    LegalDocumentDTO updateDocument(Long id, LegalDocumentDTO document);
}
