package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryReportExport;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReportExportRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReportService {

    private static final long MAX_PDF_SIZE = 100L * 1024 * 1024;
    private static final Duration MAX_GENERATION_TIME = Duration.ofSeconds(120);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InventoryService inventoryService;
    private final UsersService usersService;
    private final TenantsService tenantsService;
    private final InventoryReportExportRepository reportExportRepository;

    public InventoryReportService(
        InventoryService inventoryService,
        UsersService usersService,
        TenantsService tenantsService,
        InventoryReportExportRepository reportExportRepository
    ) {
        this.inventoryService = inventoryService;
        this.usersService = usersService;
        this.tenantsService = tenantsService;
        this.reportExportRepository = reportExportRepository;
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
            PageWriter writer = new PageWriter(document, regular, bold);
            writer.title("Prospetto inventario consegnato e riconsegnato");
            TenantsDTO tenant = tenantsService.findByCode(requiredTenant(token), token).orElse(null);
            writer.line((tenant == null ? requiredTenant(token) : safe(tenant.getName())), true);
            if (tenant != null) {
                writer.line("Sede: " + safe(joinAddress(tenant)), false);
                writer.line("Codice fiscale: " + safe(tenant.getTaxCode()) + " - Partita IVA: " + safe(tenant.getVatNumber()), false);
            }
            writer.line("Generato il: " + DATE_TIME.format(ZonedDateTime.now()), false);
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
                writer.line("Stato di conservazione: " + assignment.conditionStatus(), false);
                writer.line("Note di conservazione: " + safe(assignment.conditionNotes()), false);
                writer.line("Quantità assegnata: " + assignment.assignedQuantity(), false);
                writer.line("Quantità riconsegnata: " + assignment.returnedQuantity(), false);
                writer.line("Quantità ancora consegnata: " + assignment.outstandingQuantity(), false);
                writer.line("Data assegnazione: " + DATE_TIME.format(assignment.assignedAt()), false);
                writer.line("Stato: " + assignment.status(), false);
                writer.line("Descrizione assegnazione: " + safe(assignment.description()), false);
                writer.line("Revisione presa visione: " + assignment.revision() + " - hash SHA-256 " + assignment.revisionHash(), false);
                if (assignment.decision() == null) {
                    writer.line("Presa visione: in attesa", true);
                } else {
                    writer.line("Presa visione: " + assignment.decision().decision() + " il " + DATE_TIME.format(assignment.decision().decidedAt()), true);
                    if (assignment.decision().rejectionReason() != null) writer.line("Motivazione: " + assignment.decision().rejectionReason(), false);
                }
                if (!assignment.returns().isEmpty()) {
                    writer.subheading("Riconsegne");
                    for (InventoryReturnDTO inventoryReturn : assignment.returns()) {
                        String completed = inventoryReturn.completedAt() == null ? "" : ", completata il " + DATE_TIME.format(inventoryReturn.completedAt());
                        writer.line("- Quantità " + inventoryReturn.quantity() + ", stato " + inventoryReturn.status() + completed, false);
                        if (inventoryReturn.condition() != null) writer.line("  Conservazione alla riconsegna: " + inventoryReturn.condition(), false);
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
            addFooters(document, regular);
            checkTimeout(startedAt);
            document.save(output);
            checkTimeout(startedAt);
            if (output.size() > MAX_PDF_SIZE) throw reportTooLarge("Il PDF generato supera il limite di 100 MB");
            byte[] bytes = output.toByteArray();
            auditExport(requestedUserIndex, includeAssigned, includeReturned, includePhotos, bytes, token);
            String surname = safe(user.getLastName()).replaceAll("[^A-Za-z0-9_-]", "_");
            return new ReportContent("prospetto-inventario-" + surname + ".pdf", bytes);
        } catch (IOException e) {
            throw new RequestAlertException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile generare il prospetto PDF", "inventoryReport", "inventory.report.generationFailed");
        }
    }

    private void auditExport(
        Long requestedUserIndex,
        boolean includeAssigned,
        boolean includeReturned,
        boolean includePhotos,
        byte[] bytes,
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
        export.setFileSize(bytes.length);
        export.setContentDigest(sha256(bytes));
        reportExportRepository.save(export);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    private static void addFooters(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                String text = "Pagina " + (i + 1) + " di " + total;
                float width = font.getStringWidth(text) / 1000 * 8;
                stream.beginText();
                stream.setFont(font, 8);
                stream.setNonStrokingColor(new java.awt.Color(90, 90, 90));
                stream.newLineAtOffset((page.getMediaBox().getWidth() - width) / 2, 22);
                stream.showText(text);
                stream.endText();
            }
        }
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

    private static String joinAddress(TenantsDTO tenant) {
        return java.util.stream.Stream.of(tenant.getAddress(), tenant.getPostalCode(), tenant.getCity(), tenant.getProvince(), tenant.getCountry())
            .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record ReportContent(String fileName, byte[] bytes) {}

    private static final class PageWriter {
        private static final float MARGIN = 48;
        private static final float BOTTOM = 44;
        private final PDDocument document;
        private final PDFont regular;
        private final PDFont bold;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PageWriter(PDDocument document, PDFont regular, PDFont bold) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        void title(String text) throws IOException { writeWrapped(text, bold, 18, 24, 0, 28, 38, 76, 115); }
        void heading(String text) throws IOException { ensure(38); writeWrapped(text, bold, 13, 18, 0, 32, 71, 110, 153); }
        void subheading(String text) throws IOException { ensure(28); writeWrapped(text, bold, 11, 16, 0, 50, 75, 105, 130); }
        void line(String text, boolean emphasize) throws IOException { writeWrapped(text, emphasize ? bold : regular, 9.5f, 14, 0, 35, 35, 35, 35); }
        void space(float value) throws IOException { ensure(value); y -= value; }

        void separator() throws IOException {
            ensure(24);
            y -= 8;
            stream.setStrokingColor(new java.awt.Color(205, 210, 216));
            stream.moveTo(MARGIN, y);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            stream.stroke();
            y -= 14;
        }

        void image(byte[] bytes, String caption) throws IOException {
            ensure(180);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, caption);
            float maxWidth = page.getMediaBox().getWidth() - 2 * MARGIN;
            float maxHeight = 160;
            float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
            float width = image.getWidth() * scale;
            float height = image.getHeight() * scale;
            stream.drawImage(image, MARGIN, y - height, width, height);
            y -= height + 4;
            writeWrapped("Foto: " + caption, regular, 8, 11, 0, 85, 85, 85, 85);
            y -= 6;
        }

        void closeCurrentPage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void writeWrapped(String source, PDFont font, float size, float leading, float indent, int red, int green, int blue, int ignored) throws IOException {
            List<String> lines = wrap(normalize(source), font, size, page.getMediaBox().getWidth() - 2 * MARGIN - indent);
            ensure(lines.size() * leading + 2);
            stream.setNonStrokingColor(new java.awt.Color(red, green, blue));
            stream.setFont(font, size);
            for (String line : lines) {
                stream.beginText();
                stream.newLineAtOffset(MARGIN + indent, y);
                stream.showText(line);
                stream.endText();
                y -= leading;
            }
        }

        private void ensure(float required) throws IOException {
            if (y - required < BOTTOM) newPage();
        }

        private void newPage() throws IOException {
            closeCurrentPage();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> result = new ArrayList<>();
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            result.add(line.isEmpty() ? "-" : line.toString());
            return result;
        }

        private static String normalize(String text) {
            return text.replace('\u2013', '-').replace('\u2014', '-').replace('\u2011', '-').replace('\u00a0', ' ');
        }
    }
}
