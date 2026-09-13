package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.inventory.*;
import com.fundaro.zodiac.taurus.service.impl.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiresTenantFeature(TenantFeature.INVENTORY)
public class InventoryResource {

    private final InventoryService inventoryService;
    private final InventoryReportService inventoryReportService;
    private final InventoryErasureService inventoryErasureService;
    private final ApplicationProperties.DashboardProperties dashboardProperties;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final InventoryQrCodeService inventoryQrCodeService;
    private final InventoryLabelService inventoryLabelService;
    private final InventoryIssueService inventoryIssueService;

    public InventoryResource(
        InventoryService inventoryService,
        InventoryReportService inventoryReportService,
        InventoryErasureService inventoryErasureService,
        ApplicationProperties applicationProperties,
        TenantTimeZoneService tenantTimeZoneService,
        InventoryQrCodeService inventoryQrCodeService,
        InventoryLabelService inventoryLabelService,
        InventoryIssueService inventoryIssueService
    ) {
        this.inventoryService = inventoryService;
        this.inventoryReportService = inventoryReportService;
        this.inventoryErasureService = inventoryErasureService;
        this.dashboardProperties = applicationProperties.getDashboard();
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.inventoryQrCodeService = inventoryQrCodeService;
        this.inventoryLabelService = inventoryLabelService;
        this.inventoryIssueService = inventoryIssueService;
    }

    @GetMapping("/items")
    public Page<InventoryItemDTO> getItems(@RequestParam(required = false) String query, @RequestParam(required = false) String attention, Pageable pageable, AbstractAuthenticationToken token) {
        return inventoryService.findItems(
            query,
            attention,
            LocalDate.now(tenantTimeZoneService.currentZoneId()).plusDays(dashboardProperties.getInventoryExpirationLookAheadDays()),
            pageable,
            token
        );
    }

    @GetMapping("/summary")
    public InventoryAdminSummaryDTO getSummary(AbstractAuthenticationToken token) {
        return inventoryService.getAdminSummary(token);
    }

    @GetMapping("/items/{id}")
    public InventoryItemDTO getItem(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryService.findItem(id, token);
    }

    @PostMapping("/items")
    public ResponseEntity<InventoryItemDTO> createItem(@Valid @RequestBody InventoryItemRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(inventoryService.createItem(request, token));
    }

    @PutMapping("/items/{id}")
    public InventoryItemDTO updateItem(@PathVariable long id, @Valid @RequestBody InventoryItemRequest request, AbstractAuthenticationToken token) {
        return inventoryService.updateItem(id, request, token);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable long id, AbstractAuthenticationToken token) {
        inventoryService.deleteItem(id, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items/{id}/assignments")
    public ResponseEntity<InventoryAssignmentDTO> assign(@PathVariable long id, @Valid @RequestBody InventoryAssignmentRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(inventoryService.assign(id, request, token));
    }

    @PutMapping("/assignments/{id}")
    public InventoryAssignmentDTO updateAssignment(@PathVariable long id, @Valid @RequestBody InventoryAssignmentRequest request, AbstractAuthenticationToken token) {
        return inventoryService.updateAssignment(id, request, token);
    }

    @DeleteMapping("/assignments/{id}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable long id, AbstractAuthenticationToken token) {
        inventoryService.deleteAssignment(id, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/assignments/{id}/reissue")
    public InventoryAssignmentDTO reissue(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryService.reissue(id, token);
    }

    @GetMapping("/users/{userIndex}/assignments")
    public List<InventoryAssignmentDTO> getUserAssignments(@PathVariable Long userIndex, AbstractAuthenticationToken token) {
        return inventoryService.findUserAssignments(userIndex, token);
    }

    @PostMapping("/assignments/{id}/returns")
    public ResponseEntity<InventoryReturnDTO> requestReturn(@PathVariable long id, @Valid @RequestBody InventoryReturnRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(201).body(inventoryService.requestReturn(id, request, false, token));
    }

    @PostMapping("/returns/{id}/complete")
    public InventoryReturnDTO completeReturn(@PathVariable long id, @Valid @RequestBody InventoryReturnRequest request, AbstractAuthenticationToken token) {
        return inventoryService.completeReturn(id, request, token);
    }

    @PostMapping("/returns/{id}/cancel")
    public InventoryReturnDTO cancelReturn(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryService.cancelReturn(id, token);
    }

    @DeleteMapping("/returns/{id}")
    public ResponseEntity<Void> deleteReturn(@PathVariable long id, AbstractAuthenticationToken token) {
        inventoryService.deleteReturn(id, token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/items/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventoryPhotoDTO> addPhoto(@PathVariable long id, @RequestPart("file") MultipartFile file, AbstractAuthenticationToken token) throws IOException {
        return ResponseEntity.status(201).body(inventoryService.addPhoto(id, file, token));
    }

    @PostMapping(value = "/returns/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InventoryPhotoDTO> addReturnPhoto(@PathVariable long id, @RequestPart("file") MultipartFile file, AbstractAuthenticationToken token) throws IOException {
        return ResponseEntity.status(201).body(inventoryService.addReturnPhoto(id, file, false, token));
    }

    @GetMapping("/return-photos/{id}")
    public ResponseEntity<byte[]> getReturnPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return photoResponse(inventoryService.getReturnPhoto(id, false, token));
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> getPhoto(@PathVariable long id, AbstractAuthenticationToken token) throws IOException {
        return photoResponse(inventoryService.getPhoto(id, false, token));
    }

    @DeleteMapping("/photos/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable long id, AbstractAuthenticationToken token) {
        inventoryService.deletePhoto(id, token);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/items/{id}/photos/order")
    public List<InventoryPhotoDTO> reorderPhotos(
        @PathVariable long id,
        @Valid @RequestBody InventoryPhotoOrderRequest request,
        AbstractAuthenticationToken token
    ) {
        return inventoryService.reorderPhotos(id, request, token);
    }

    @PutMapping("/items/{itemId}/photos/{photoId}/preview")
    public List<InventoryPhotoDTO> setPreviewPhoto(
        @PathVariable long itemId,
        @PathVariable long photoId,
        AbstractAuthenticationToken token
    ) {
        return inventoryService.setPreviewPhoto(itemId, photoId, token);
    }

    @GetMapping(value = "/users/{userIndex}/report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getUserReport(
        @PathVariable Long userIndex,
        @RequestParam(defaultValue = "true") boolean includeAssigned,
        @RequestParam(defaultValue = "true") boolean includeReturned,
        @RequestParam(defaultValue = "true") boolean includePhotos,
        AbstractAuthenticationToken token
    ) {
        InventoryReportService.ReportContent report = inventoryReportService.createForUser(userIndex, includeAssigned, includeReturned, includePhotos, token);
        return reportResponse(report);
    }

    @GetMapping("/erasure-requests")
    public List<InventoryErasureRequestDTO> getPendingErasureRequests(AbstractAuthenticationToken token) {
        return inventoryErasureService.findPending(token);
    }

    @PostMapping("/erasure-requests/{id}/complete")
    public InventoryErasureRequestDTO completeErasureRequest(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryErasureService.complete(id, token);
    }

    @GetMapping(value = "/items/{id}/qr-code.png", produces = MediaType.IMAGE_PNG_VALUE)
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public ResponseEntity<byte[]> getQrCode(@PathVariable long id, @RequestParam(defaultValue = "256") int size, AbstractAuthenticationToken token) {
        return binaryResponse(inventoryLabelService.png(id, size, token), ContentDisposition.inline());
    }

    @PostMapping(value = "/labels", produces = MediaType.APPLICATION_PDF_VALUE)
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public ResponseEntity<byte[]> createLabels(@Valid @RequestBody InventoryQrDtos.LabelRequest request, AbstractAuthenticationToken token) {
        return binaryResponse(inventoryLabelService.labels(request, token), ContentDisposition.attachment());
    }

    @PostMapping("/items/{id}/qr-code/rotate")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public InventoryQrDtos.RotateResponse rotateQrCode(
        @PathVariable long id,
        @Valid @RequestBody InventoryQrDtos.RotateRequest request,
        AbstractAuthenticationToken token
    ) {
        return inventoryQrCodeService.rotate(id, request.reason(), token);
    }

    @GetMapping("/items/{id}/issues")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public List<InventoryIssueDtos.Response> getIssues(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryIssueService.findForItem(id, token);
    }

    @PostMapping("/items/{id}/issues")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public ResponseEntity<InventoryIssueDtos.Response> createIssue(
        @PathVariable long id,
        @Valid @RequestBody InventoryIssueDtos.CreateRequest request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.status(201).body(inventoryIssueService.createAdmin(id, request, token));
    }

    @GetMapping("/issues/{id}")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public InventoryIssueDtos.Response getIssue(@PathVariable long id, AbstractAuthenticationToken token) {
        return inventoryIssueService.findAdmin(id, token);
    }

    @PatchMapping("/issues/{id}/status")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public InventoryIssueDtos.Response transitionIssue(
        @PathVariable long id,
        @Valid @RequestBody InventoryIssueDtos.TransitionRequest request,
        AbstractAuthenticationToken token
    ) {
        return inventoryIssueService.transition(id, request, token);
    }

    @PostMapping(value = "/issues/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public ResponseEntity<InventoryIssueDtos.Photo> addIssuePhoto(
        @PathVariable long id,
        @RequestPart("file") MultipartFile file,
        AbstractAuthenticationToken token
    ) throws IOException {
        return ResponseEntity.status(201).body(inventoryIssueService.addPhoto(id, file, false, token));
    }

    @GetMapping("/issue-photos/{id}")
    @RequiresTenantFeature(TenantFeature.INVENTORY_QR)
    public ResponseEntity<byte[]> getIssuePhoto(@PathVariable long id, AbstractAuthenticationToken token) {
        return photoResponse(inventoryIssueService.getPhoto(id, false, token));
    }

    public static ResponseEntity<byte[]> photoResponse(InventoryService.PhotoContent photo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(photo.contentType()));
        headers.setContentDisposition(ContentDisposition.inline().filename(photo.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(photo.bytes());
    }

    public static ResponseEntity<byte[]> reportResponse(InventoryReportService.ReportContent report) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(report.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(report.bytes());
    }

    private static ResponseEntity<byte[]> binaryResponse(InventoryQrDtos.BinaryContent content, ContentDisposition.Builder disposition) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentDisposition(disposition.filename(content.fileName()).build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).body(content.bytes());
    }
}
