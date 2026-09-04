package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryReportExport;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReportExportRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.service.report.ReportLabels;
import com.fundaro.zodiac.taurus.utils.pdf.PdfPageWriter;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReportService {

    private static final long MAX_PDF_SIZE = 100L * 1024 * 1024;
    private static final Duration MAX_GENERATION_TIME = Duration.ofSeconds(120);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventoryService inventoryService;
    private final UsersService usersService;
    private final InventoryReportExportRepository reportExportRepository;
    private final TenantPdfHeaderService tenantPdfHeaderService;
    private final MediaService mediaService;
    private final MediaRepository mediaRepository;

    public InventoryReportService(
        InventoryService inventoryService,
        UsersService usersService,
        InventoryReportExportRepository reportExportRepository,
        TenantPdfHeaderService tenantPdfHeaderService,
        MediaService mediaService,
        MediaRepository mediaRepository
    ) {
        this.inventoryService = inventoryService;
        this.usersService = usersService;
        this.reportExportRepository = reportExportRepository;
        this.tenantPdfHeaderService = tenantPdfHeaderService;
        this.mediaService = mediaService;
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public ReportContent createOwn(boolean includeAssigned, boolean includeReturned, boolean includePhotos, AbstractAuthenticationToken token) {
        UsersDTO user = usersService.findMe(token).orElseThrow(() -> notFound("Utente autenticato non trovato"));
        Long requestedUserIndex = Objects.requireNonNull(user.getId());
        return generate(user, requestedUserIndex, inventoryService.findOwnAssignments(token), includeAssigned, includeReturned, includePhotos, true, token);
    }

    @Transactional
    public ReportContent createForUser(Long userIndex, boolean includeAssigned, boolean includeReturned, boolean includePhotos, AbstractAuthenticationToken token) {
        UsersDTO user = usersService.findOne(userIndex, token).orElseThrow(() -> notFound("Utente non trovato"));
        return generate(user, userIndex, inventoryService.findUserAssignments(userIndex, token), includeAssigned, includeReturned, includePhotos, false, token);
    }

    private ReportContent generate(
        UsersDTO user,
        Long requestedUserIndex,
        List<InventoryAssignmentDTO> source,
        boolean includeAssigned,
        boolean includeReturned,
        boolean includePhotos,
        boolean ownerPhotoAccess,
        AbstractAuthenticationToken token
    ) {
        if (!includeAssigned && !includeReturned) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Selezionare almeno una tipologia di materiale", "inventoryReport", "inventory.report.emptyFilter");
        }
        ZonedDateTime startedAt = ZonedDateTime.now();
        List<InventoryAssignmentDTO> assignments = source.stream()
            .filter(value -> (includeAssigned && value.outstandingQuantity() > 0) || (includeReturned && value.returnedQuantity() > 0))
            .toList();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PdfPageWriter writer = new PdfPageWriter(document, regular, bold);
            tenantPdfHeaderService.write(writer, "Prospetto inventario consegnato e riconsegnato", requiredTenant(token), startedAt, token);
            writer.space(10);
            writer.heading("Utente");
            writer.line("Nome e cognome: " + safe(user.getName()) + " " + safe(user.getLastName()), false);
            writer.line("Email: " + safe(user.getEmail()), false);
            if (user.getBirthDate() != null) writer.line("Nato il: " + formatDate(user.getBirthDate()), false);
            writer.space(12);

            if (assignments.isEmpty()) {
                writer.line("Nessun oggetto corrisponde ai filtri selezionati.", false);
            }
            int photoCount = 0;
            for (InventoryAssignmentDTO assignment : assignments) {
                checkTimeout(startedAt);
                writer.heading(assignment.inventoryNumber() + " - " + assignment.itemName());
                writer.line("Descrizione oggetto: " + safe(assignment.itemDescription()), false);
                writer.line("Valore unitario stimato: " + (assignment.estimatedUnitValue() == null ? "-" : assignment.estimatedUnitValue().toPlainString() + " " + safe(assignment.currency())), false);
                writer.line("Stato di conservazione: " + ReportLabels.inventoryCondition(assignment.conditionStatus()), false);
                writer.line("Note di conservazione: " + safe(assignment.conditionNotes()), false);
                writer.line("Data di scadenza: " + (assignment.expirationDate() == null ? "-" : DATE.format(assignment.expirationDate())), false);
                writer.line("Quantità assegnata: " + assignment.assignedQuantity(), false);
                writer.line("Quantità riconsegnata: " + assignment.returnedQuantity(), false);
                writer.line("Quantità ancora consegnata: " + assignment.outstandingQuantity(), false);
                writer.line("Data assegnazione: " + DATE_TIME.format(assignment.assignedAt()), false);
                writer.line("Stato: " + ReportLabels.inventoryAssignmentStatus(assignment.status()), false);
                writer.line("Descrizione assegnazione: " + safe(assignment.description()), false);
                writer.line("Revisione presa visione: " + assignment.revision() + " - hash SHA-256 " + assignment.revisionHash(), false);
                if (assignment.decision() == null) {
                    writer.line("Presa visione: in attesa", true);
                } else {
                    writer.line("Presa visione: " + ReportLabels.inventoryDecision(assignment.decision().decision()) + " il " + DATE_TIME.format(assignment.decision().decidedAt()), true);
                    if (assignment.decision().rejectionReason() != null) writer.line("Motivazione: " + assignment.decision().rejectionReason(), false);
                }
                if (!assignment.returns().isEmpty()) {
                    writer.subheading("Riconsegne");
                    for (InventoryReturnDTO inventoryReturn : assignment.returns()) {
                        String completed = inventoryReturn.completedAt() == null ? "" : ", completata il " + DATE_TIME.format(inventoryReturn.completedAt());
                        writer.line("- Quantità " + inventoryReturn.quantity() + ", stato " + ReportLabels.inventoryReturnStatus(inventoryReturn.status()) + completed, false);
                        if (inventoryReturn.condition() != null) writer.line("  Conservazione alla riconsegna: " + ReportLabels.inventoryCondition(inventoryReturn.condition()), false);
                        if (inventoryReturn.notes() != null) writer.line("  Note: " + inventoryReturn.notes(), false);
                        if (includePhotos) {
                            for (InventoryPhotoDTO returnPhoto : inventoryReturn.photos()) {
                                if (++photoCount > 100) throw reportTooLarge("Il report supera il limite di 100 fotografie");
                                InventoryService.PhotoContent content = inventoryService.getReturnPhoto(returnPhoto.id(), ownerPhotoAccess, token);
                                writer.image(content.bytes(), "Riconsegna - " + returnPhoto.fileName());
                                checkTimeout(startedAt);
                            }
                        }
                    }
                }
                if (includePhotos && !assignment.photos().isEmpty()) {
                    writer.subheading("Fotografie allegate");
                    for (InventoryPhotoDTO photo : assignment.photos()) {
                        if (++photoCount > 100) throw reportTooLarge("Il report supera il limite di 100 fotografie");
                        checkTimeout(startedAt);
                        InventoryService.PhotoContent content = inventoryService.getPhoto(photo.id(), ownerPhotoAccess, token);
                        writer.image(content.bytes(), photo.fileName());
                        checkTimeout(startedAt);
                    }
                }
                writer.separator();
            }
            writer.closeCurrentPage();
            PdfPageWriter.addPageNumbers(document, regular);
            checkTimeout(startedAt);
            document.save(output);
            checkTimeout(startedAt);
            if (output.size() > MAX_PDF_SIZE) throw reportTooLarge("Il PDF generato supera il limite di 100 MB");
            byte[] bytes = output.toByteArray();
            String surname = safe(user.getLastName()).replaceAll("[^A-Za-z0-9_-]", "_");
            String fileName = "prospetto-inventario-" + surname + ".pdf";
            MediaDTO media = mediaService.store(bytes, fileName, "application/pdf", "inventory-reports", token);
            auditExport(requestedUserIndex, includeAssigned, includeReturned, includePhotos, media.getId(), token);
            return new ReportContent(fileName, bytes);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il prospetto PDF", "inventoryReport", "inventory.report.generationFailed");
        }
    }

    private void auditExport(
        Long requestedUserIndex,
        boolean includeAssigned,
        boolean includeReturned,
        boolean includePhotos,
        Long mediaAssetId,
        AbstractAuthenticationToken token
    ) {
        InventoryReportExport export = new InventoryReportExport();
        String actor = requiredActor(token);
        export.initializeAudit(actor);
        export.setRequestedUserIndex(requestedUserIndex);
        export.setGeneratedBy(actor);
        export.setGeneratedAt(ZonedDateTime.now());
        export.setIncludeAssigned(includeAssigned);
        export.setIncludeReturned(includeReturned);
        export.setIncludePhotos(includePhotos);
        export.setMediaAsset(mediaRepository.getReferenceById(mediaAssetId));
        reportExportRepository.save(export);
    }

    private static void checkTimeout(ZonedDateTime startedAt) {
        if (Duration.between(startedAt, ZonedDateTime.now()).compareTo(MAX_GENERATION_TIME) > 0) {
            throw new RequestAlertException(HttpStatus.REQUEST_TIMEOUT, "La generazione del PDF ha superato 120 secondi", "inventoryReport", "inventory.report.timeout");
        }
    }

    private static RequestAlertException reportTooLarge(String message) {
        return new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, message, "inventoryReport", "inventory.report.tooLarge");
    }

    private static RequestAlertException notFound(String message) {
        return new RequestAlertException(HttpStatus.NOT_FOUND, message, "inventoryReport", "inventory.report.userNotFound");
    }

    private static String requiredTenant(AbstractAuthenticationToken token) {
        return Objects.requireNonNullElse(SecurityUtils.getTenantIdFromAuthentication(token), "");
    }

    private static String requiredActor(AbstractAuthenticationToken token) {
        String actor = SecurityUtils.getUserIdFromAuthentication(token);
        if (actor == null || actor.isBlank()) {
            throw new RequestAlertException(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "inventoryReport", "inventory.identity.missing");
        }
        return actor;
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN).format(date);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record ReportContent(String fileName, byte[] bytes) {}

}
