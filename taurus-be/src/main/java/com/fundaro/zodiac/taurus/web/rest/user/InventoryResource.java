package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentScope;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnRequest;
import com.fundaro.zodiac.taurus.service.impl.InventoryReportService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController("UserInventoryResource")
@RequestMapping("/api/user/inventory")
public class InventoryResource {

    private final InventoryService inventoryService;
    private final InventoryReportService inventoryReportService;

    public InventoryResource(InventoryService inventoryService, InventoryReportService inventoryReportService) {
        this.inventoryService = inventoryService;
        this.inventoryReportService = inventoryReportService;
    }

    @GetMapping("/assignments")
    public Page<InventoryAssignmentSummaryDTO> getAssignments(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "POSSESSED") InventoryAssignmentScope scope,
        Pageable pageable,
        AbstractAuthenticationToken token
    ) {
        return inventoryService.findOwnAssignments(query, scope, pageable, token);
    }

    @GetMapping("/assignments/{id}")
    public InventoryAssignmentDTO getAssignment(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryService.findOwnAssignment(id, token);
    }

    @PostMapping("/assignments/{id}/decision")
    public InventoryAssignmentDTO decide(
        @PathVariable long id,
        @Valid @RequestBody InventoryDecisionRequest request,
        AbstractAuthenticationToken token
    ) {
        return inventoryService.decide(id, request, token);
    }

    @PostMapping("/assignments/{id}/returns")
    public ResponseEntity<InventoryReturnDTO> requestReturn(
        @PathVariable long id,
        @Valid @RequestBody InventoryReturnRequest request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.status(201).body(inventoryService.requestReturn(id, request, true, token));
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return com.fundaro.zodiac.taurus.web.rest.InventoryResource.photoResponse(inventoryService.getPhoto(id, true, token));
    }

    @PostMapping(value = "/returns/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventoryPhotoDTO> addReturnPhoto(
        @PathVariable long id,
        @RequestPart("file") MultipartFile file,
        AbstractAuthenticationToken token
    ) throws IOException {
        return ResponseEntity.status(201).body(inventoryService.addReturnPhoto(id, file, true, token));
    }

    @GetMapping("/return-photos/{id}")
    public ResponseEntity<byte[]> getReturnPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return com.fundaro.zodiac.taurus.web.rest.InventoryResource.photoResponse(inventoryService.getReturnPhoto(id, true, token));
    }

    @GetMapping(value = "/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(
        @RequestParam(defaultValue = "true") boolean includeAssigned,
        @RequestParam(defaultValue = "true") boolean includeReturned,
        @RequestParam(defaultValue = "true") boolean includePhotos,
        AbstractAuthenticationToken token
    ) {
        return com.fundaro.zodiac.taurus.web.rest.InventoryResource.reportResponse(
            inventoryReportService.createOwn(includeAssigned, includeReturned, includePhotos, token)
        );
    }
}
