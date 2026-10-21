package com.fundaro.zodiac.taurus.service.loansheet;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.samskivert.mustache.BasicCollector;
import com.samskivert.mustache.Mustache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Renders a loan sheet: the template uploaded by the tenant, or the default one, is filled with the data of
 * {@link LoanSheetModelFactory}, parsed leniently and converted to PDF by OpenHTMLtoPDF.
 *
 * <p>The template comes from the tenant, so the renderer is a sandbox: Mustache resolves names only in maps and lists,
 * without reflection, values are always HTML-escaped, partials are refused, and no URI is loaded except embedded
 * {@code data:} images. Previews get an {@code ANTEPRIMA} watermark on every page, whatever the template.
 */
@Component
public class LoanSheetPdfRenderer {

    private static final Logger LOG = LoggerFactory.getLogger(LoanSheetPdfRenderer.class);
    static final String WATERMARK = "ANTEPRIMA";
    public static final String COPY_WATERMARK = "COPIA DI CONSULTAZIONE – NON FIRMATA";
    public static final long MAX_PDF_BYTES = 20L * 1024 * 1024;

    private static final Mustache.Compiler COMPILER = Mustache.compiler()
        .withCollector(new MapsAndListsCollector())
        .escapeHTML(true)
        .emptyStringIsFalse(true)
        .defaultValue("")
        .withLoader(name -> {
            throw new IllegalArgumentException("Template includes are not allowed: " + name);
        });

    /**
     * Fills the template with the data; throws {@link com.samskivert.mustache.MustacheException} on invalid syntax.
     */
    public String html(String template, Map<String, Object> model) {
        return COMPILER.compile(template).execute(model);
    }

    /**
     * Checks the Mustache syntax of a template; throws {@link com.samskivert.mustache.MustacheException} when invalid.
     */
    public void compile(String template) {
        COMPILER.compile(template);
    }

    public byte[] render(String template, Map<String, Object> model, boolean preview) {
        byte[] pdf = toPdf(Jsoup.parse(html(template, model)));
        return preview ? watermark(pdf, WATERMARK) : pdf;
    }

    /**
     * Renders only the parts of the template that belong to an act signed electronically, as marked by
     * {@link LoanSheetActs}.
     */
    public byte[] renderAct(String template, Map<String, Object> model, String act) {
        Document document = Jsoup.parse(html(template, model));
        LoanSheetActs.filter(document, act);
        return toPdf(document);
    }

    /**
     * Renders the whole sheet with a watermark on every page, for example the copy for consultation of a sheet signed
     * electronically, whose evidential documents are the signed acts.
     */
    public byte[] renderWithWatermark(String template, Map<String, Object> model, String watermark) {
        return watermark(toPdf(Jsoup.parse(html(template, model))), watermark);
    }

    private static byte[] toPdf(Document document) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUriResolver((baseUri, uri) -> isEmbedded(uri) ? uri : null);
            builder.useExternalResourceAccessControl((uri, type) -> isEmbedded(uri), ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl((uri, type) -> isEmbedded(uri), ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.withDiagnosticConsumer(diagnostic -> LOG.debug("loan_sheet_render {}", diagnostic.getFormattedMessage()));
            builder.withW3cDocument(W3CDom.convert(document), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to render the loan sheet", exception);
        }
    }

    static boolean isEmbedded(String uri) {
        return uri != null && uri.strip().toLowerCase(Locale.ROOT).startsWith("data:");
    }

    private static byte[] watermark(byte[] pdf, String text) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDExtendedGraphicsState transparency = new PDExtendedGraphicsState();
            transparency.setNonStrokingAlphaConstant(0.15f);
            for (PDPage page : document.getPages()) {
                PDRectangle box = page.getMediaBox();
                // The text runs along the diagonal: long texts get a smaller font so that they stay on the page.
                float diagonal = (float) Math.hypot(box.getWidth(), box.getHeight());
                float fontSize = Math.min(96, diagonal * 0.8f / (font.getStringWidth(text) / 1000));
                float width = font.getStringWidth(text) / 1000 * fontSize;
                try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    stream.setGraphicsStateParameters(transparency);
                    stream.setNonStrokingColor(0.45f, 0.45f, 0.45f);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    Matrix matrix = Matrix.getRotateInstance(Math.toRadians(55), box.getWidth() / 2, box.getHeight() / 2);
                    matrix.translate(-width / 2, -fontSize / 3);
                    stream.setTextMatrix(matrix);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to add the preview watermark", exception);
        }
    }

    /**
     * Resolves names only in maps and lists: the default JMustache collector would also call methods and read fields
     * through reflection.
     */
    private static final class MapsAndListsCollector extends BasicCollector {
        @Override
        public <K, V> Map<K, V> createFetcherCache() {
            return new ConcurrentHashMap<>();
        }
    }
}
