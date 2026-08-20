package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnRequest;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.service.impl.InventoryReportService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/inventory/me")
public class InventoryMeResource {

    private final InventoryService inventoryService;
    private final InventoryReportService inventoryReportService;

    public InventoryMeResource(InventoryService inventoryService, InventoryReportService inventoryReportService) {
        this.inventoryService = inventoryService;
        this.inventoryReportService = inventoryReportService;
    }

    @GetMapping("/assignments")
    public List<InventoryAssignmentDTO> getAssignments(AbstractAuthenticationToken token) {
        return inventoryService.findOwnAssignments(token);
    }

    @PostMapping("/assignments/{id}/decision")
    public InventoryAssignmentDTO decide(@PathVariable long id, @Valid @RequestBody InventoryDecisionRequest request, AbstractAuthenticationToken token) {
        return inventoryService.decide(id, request, token);
    }

    @PostMapping("/assignments/{id}/returns")
    public ResponseEntity<InventoryReturnDTO> requestReturn(@PathVariable long id, @Valid @RequestBody InventoryReturnRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(inventoryService.requestReturn(id, request, true, token));
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return InventoryResource.photoResponse(inventoryService.getPhoto(id, true, token));
    }

    @PostMapping(value = "/returns/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoDTO> addReturnPhoto(
        @PathVariable long id,
        @RequestPart("file") MultipartFile file,
        AbstractAuthenticationToken token
    ) throws IOException {
        return ResponseEntity.status(201).body(inventoryService.addReturnPhoto(id, file, true, token));
    }

    @GetMapping("/return-photos/{id}")
    public ResponseEntity<byte[]> getReturnPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return InventoryResource.photoResponse(inventoryService.getReturnPhoto(id, true, token));
    }

    @GetMapping(value = "/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getReport(
        @RequestParam(defaultValue = "true") boolean includeAssigned,
        @RequestParam(defaultValue = "true") boolean includeReturned,
        @RequestParam(defaultValue = "true") boolean includePhotos,
        AbstractAuthenticationToken token
    ) {
        return InventoryResource.reportResponse(inventoryReportService.createOwn(includeAssigned, includeReturned, includePhotos, token));
    }
}
