package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.LegalAcceptanceRequestDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.dto.LegalStatusDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/legal")
public class LegalResource {

    private final LegalService legalService;

    public LegalResource(LegalService legalService) {
        this.legalService = legalService;
    }

    @GetMapping("/status")
    public ResponseEntity<LegalStatusDTO> getStatus(AbstractAuthenticationToken authenticationToken) {
        return ResponseEntity.ok(legalService.getStatus(authenticationToken));
    }

    @PostMapping("/acceptances")
    public ResponseEntity<LegalStatusDTO> accept(
        @Valid @RequestBody LegalAcceptanceRequestDTO request,
        AbstractAuthenticationToken authenticationToken
    ) {
        return ResponseEntity.ok(legalService.accept(request, authenticationToken));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<LegalDocumentDTO>> getDocuments() {
        return ResponseEntity.ok(legalService.findAllDocuments());
    }

    @PostMapping("/documents")
    public ResponseEntity<LegalDocumentDTO> createDocument(@Valid @RequestBody LegalDocumentDTO document) {
        LegalDocumentDTO result = legalService.createDocument(document);
        return ResponseEntity.created(URI.create("/api/legal/documents/" + result.getId())).body(result);
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<LegalDocumentDTO> updateDocument(@PathVariable Long id, @Valid @RequestBody LegalDocumentDTO document) {
        return ResponseEntity.ok(legalService.updateDocument(id, document));
    }
}
