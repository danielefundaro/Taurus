package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.BinaryContent;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelEntry;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelLayout;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.LabelRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryLabelService {
    private static final float POINTS_PER_MM = 72f / 25.4f;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
    private final InventoryItemRepository itemRepository;
    private final InventoryQrCodeService qrCodeService;
    private final TenantsService tenantsService;

    public InventoryLabelService(InventoryItemRepository itemRepository, InventoryQrCodeService qrCodeService, TenantsService tenantsService) {
        this.itemRepository = itemRepository;
        this.qrCodeService = qrCodeService;
        this.tenantsService = tenantsService;
    }

    @Transactional(readOnly = true)
    public BinaryContent png(long itemId, int size, AbstractAuthenticationToken token) {
        requireTenant(token);
        if (size != 256 && size != 512) throw badRequest("La dimensione PNG deve essere 256 o 512 pixel", "inventory.qr.size.invalid");
        InventoryItem item = itemRepository.findByIdAndDeletedFalse(itemId).orElseThrow(InventoryLabelService::notFound);
        try {
            BitMatrix matrix = matrix(qrCodeService.publicUrl(item), size, size);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) image.setRGB(x, y, matrix.get(x, y) ? 0xff000000 : 0xffffffff);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return new BinaryContent("qr-inventario-" + item.getInventoryNumber() + ".png", "image/png", output.toByteArray());
        } catch (WriterException | IOException exception) {
            throw new IllegalStateException("Impossibile generare il QR code", exception);
        }
    }

    @Transactional(readOnly = true)
    public BinaryContent labels(LabelRequest request, AbstractAuthenticationToken token) {
        String tenantCode = requireTenant(token);
        qrCodeService.requireEnabled();
        validateLimits(request);
        Map<Long, InventoryItem> items = new LinkedHashMap<>();
        List<Long> ids = request.entries().stream().map(LabelEntry::itemId).distinct().toList();
        itemRepository.findAllByIdInAndDeletedFalse(ids).forEach(item -> items.put(item.getId(), item));
        if (items.size() != ids.size()) throw notFound();
        List<LabelData> labels = new ArrayList<>();
        for (LabelEntry entry : request.entries()) {
            InventoryItem item = items.get(entry.itemId());
            for (int copy = 0; copy < entry.copies(); copy++) labels.add(new LabelData(item, qrCodeService.publicUrl(item)));
        }
        String tenantName = tenantsService.findByCode(tenantCode, token).map(TenantsDTO::getName).filter(value -> !value.isBlank()).orElse(tenantCode);
        try {
            byte[] bytes = request.layout() == LabelLayout.SINGLE_62X40
                ? renderSingle(labels, tenantName)
                : renderA4(labels, tenantName, request.startCell(), request.showCutMarks());
            return new BinaryContent("etichette-inventario-" + FILE_TIME.format(ZonedDateTime.now()) + ".pdf", "application/pdf", bytes);
        } catch (IOException | WriterException exception) {
            throw new IllegalStateException("Impossibile generare le etichette inventario", exception);
        }
    }

    private static byte[] renderSingle(List<LabelData> labels, String tenantName) throws IOException, WriterException {
        try (PDDocument document = new PDDocument()) {
            for (LabelData label : labels) {
                PDPage page = new PDPage(new PDRectangle(mm(62), mm(40)));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    drawLabel(content, label, tenantName, 0, 0, mm(62), mm(40));
                }
            }
            return save(document);
        }
    }

    private static byte[] renderA4(List<LabelData> labels, String tenantName, int startCell, boolean cutMarks) throws IOException, WriterException {
        try (PDDocument document = new PDDocument()) {
            float margin = mm(5), horizontalGap = mm(3), verticalGap = mm(3);
            float cellWidth = (PDRectangle.A4.getWidth() - 2 * margin - 2 * horizontalGap) / 3;
            float cellHeight = (PDRectangle.A4.getHeight() - 2 * margin - 7 * verticalGap) / 8;
            int index = 0;
            int firstCell = startCell;
            while (index < labels.size()) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (int cell = firstCell; cell < 24 && index < labels.size(); cell++, index++) {
                        int column = cell % 3;
                        int row = cell / 3;
                        float x = margin + column * (cellWidth + horizontalGap);
                        float y = PDRectangle.A4.getHeight() - margin - (row + 1) * cellHeight - row * verticalGap;
                        drawLabel(content, labels.get(index), tenantName, x, y, cellWidth, cellHeight);
                        if (cutMarks) drawCutMarks(content, x, y, cellWidth, cellHeight);
                    }
                }
                firstCell = 0;
            }
            return save(document);
        }
    }

    private static void drawLabel(PDPageContentStream content, LabelData label, String tenantName, float x, float y, float width, float height) throws IOException, WriterException {
        float padding = mm(2);
        float qrSide = Math.min(mm(24), height - 2 * padding);
        drawQr(content, matrix(label.url(), 0, 0), x + padding, y + (height - qrSide) / 2, qrSide);
        float textX = x + padding + qrSide + mm(2);
        float textWidth = Math.max(mm(15), width - (textX - x) - padding);
        float cursor = y + height - padding - 7;
        cursor = text(content, truncate(tenantName, 34), textX, cursor, 7, true);
        cursor = text(content, truncate(label.item().getInventoryNumber(), 28), textX, cursor - 2, 9, true);
        for (String line : wrap(label.item().getName(), 25, 2)) cursor = text(content, line, textX, cursor - 2, 7, false);
        String suffix = label.item().getQrPublicId().toString().replace("-", "");
        suffix = suffix.substring(suffix.length() - 8).toUpperCase();
        cursor = text(content, "Rif. " + suffix, textX, cursor - 2, 6, false);
        text(content, "Scansiona con Taurus", textX, Math.max(y + padding, cursor - 2), 6, false);
    }

    private static void drawQr(PDPageContentStream content, BitMatrix matrix, float x, float y, float side) throws IOException {
        content.setNonStrokingColor(1f, 1f, 1f);
        content.addRect(x, y, side, side);
        content.fill();
        content.setNonStrokingColor(0f, 0f, 0f);
        float module = side / matrix.getWidth();
        for (int row = 0; row < matrix.getHeight(); row++) for (int column = 0; column < matrix.getWidth(); column++) {
            if (matrix.get(column, row)) content.addRect(x + column * module, y + side - (row + 1) * module, module + 0.02f, module + 0.02f);
        }
        content.fill();
    }

    private static void drawCutMarks(PDPageContentStream content, float x, float y, float width, float height) throws IOException {
        content.setStrokingColor(160f / 255f, 160f / 255f, 160f / 255f);
        content.setLineWidth(0.25f);
        content.addRect(x, y, width, height);
        content.stroke();
    }

    private static float text(PDPageContentStream content, String value, float x, float y, float size, boolean bold) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(bold ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA), size);
        content.newLineAtOffset(x, y);
        content.showText(ascii(value));
        content.endText();
        return y - size;
    }

    private static BitMatrix matrix(String value, int width, int height) throws WriterException {
        Map<EncodeHintType, Object> hints = Map.of(
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q,
            EncodeHintType.MARGIN, 4,
            EncodeHintType.CHARACTER_SET, "UTF-8"
        );
        return new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, width, height, hints);
    }

    private static void validateLimits(LabelRequest request) {
        if (request.entries().stream().map(LabelEntry::itemId).distinct().count() != request.entries().size())
            throw badRequest("Ogni oggetto può comparire una sola volta", "inventory.labels.duplicate");
        int total = request.entries().stream().mapToInt(LabelEntry::copies).sum();
        if (request.layout() == LabelLayout.SINGLE_62X40 && (request.startCell() != 0 || total > 20))
            throw badRequest("Il layout singolo ammette al massimo 20 etichette e startCell 0", "inventory.labels.limit");
        if (request.layout() == LabelLayout.A4_GRID_3X8 && total > 240 - request.startCell())
            throw badRequest("Il foglio A4 non può superare 10 pagine", "inventory.labels.limit");
    }

    private static byte[] save(PDDocument document) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        document.save(output);
        return output.toByteArray();
    }
    private static float mm(float value) { return value * POINTS_PER_MM; }
    private static String truncate(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static List<String> wrap(String value, int max, int lines) {
        String normalized = value == null ? "" : value.trim();
        List<String> result = new ArrayList<>();
        while (!normalized.isEmpty() && result.size() < lines) {
            int cut = Math.min(max, normalized.length());
            if (cut < normalized.length()) {
                int space = normalized.lastIndexOf(' ', cut);
                if (space > 0) cut = space;
            }
            String line = normalized.substring(0, cut).trim();
            normalized = normalized.substring(cut).trim();
            if (result.size() == lines - 1 && !normalized.isEmpty()) line = truncate(line, Math.max(2, max - 1));
            result.add(line);
        }
        return result;
    }
    private static String ascii(String value) { return value.replace('’', '\'').replace('“', '"').replace('”', '"').replace('–', '-').replace('…', '.'); }
    private static String requireTenant(AbstractAuthenticationToken token) {
        String tenant = SecurityUtils.getTenantIdFromAuthentication(token);
        if (tenant == null || tenant.isBlank()) throw badRequest("Tenant non disponibile", "inventory.tenant.missing");
        return tenant;
    }
    private static RequestAlertException notFound() { return new RequestAlertException(HttpStatus.NOT_FOUND, "Oggetto inventario non disponibile", "inventoryLabels", "inventory.notFound"); }
    private static RequestAlertException badRequest(String message, String key) { return new RequestAlertException(HttpStatus.BAD_REQUEST, message, "inventoryLabels", key); }
    private record LabelData(InventoryItem item, String url) {}
}
