package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.LegalService;
import com.fundaro.zodiac.taurus.service.dto.PublicLegalDocumentDTO;
import com.fundaro.zodiac.taurus.service.legal.LegalContent;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Published versions of the legal documents, read by anyone without login: the version in force of a document and
 * every published version by its address. Drafts answer like unknown versions.
 */
@RestController
@RequestMapping("/api/legal-documents/v1")
@TenantSchemaNotRequired
public class PublicLegalDocumentResource {

    private static final CacheControl CACHE = CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic();

    private final LegalService legalService;

    public PublicLegalDocumentResource(LegalService legalService) {
        this.legalService = legalService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<PublicLegalDocumentDTO> getInForce(@PathVariable String type) {
        return cached(legalService.findPublished(type));
    }

    @GetMapping("/{type}/{version}")
    public ResponseEntity<PublicLegalDocumentDTO> getVersion(@PathVariable String type, @PathVariable String version) {
        return cached(legalService.findPublished(type, version));
    }

    /** The tag changes with the text, the state of the version and the list of the other versions. */
    private static ResponseEntity<PublicLegalDocumentDTO> cached(PublicLegalDocumentDTO document) {
        String versions = document.versions().stream().map(version -> version.version() + ':' + version.active()).collect(Collectors.joining(","));
        String fingerprint = String.join("|", document.version(), String.valueOf(document.contentSha256()), String.valueOf(document.url()), String.valueOf(document.active()), versions);
        return ResponseEntity.ok().cacheControl(CACHE).eTag(LegalContent.sha256(fingerprint).substring(0, 32)).body(document);
    }
}
