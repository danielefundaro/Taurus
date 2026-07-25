package com.fundaro.zodiac.taurus.utils.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class PdfStructureAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(PdfStructureAnalyzer.class);

    private PdfStructureAnalyzer() {}

    /**
     * Level 1: derive page groups from PDF bookmarks/outline.
     * Falls back to a single group covering all pages when no usable outline is found.
     */
    public static List<PageGroup> analyzePageGroups(PDDocument document) {
        int totalPages = document.getNumberOfPages();
        if (totalPages == 0) return List.of();

        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null && outline.getFirstChild() != null) {
            List<PageGroup> groups = extractGroupsFromOutline(document, outline, totalPages);
            if (!groups.isEmpty()) {
                log.debug("PDF outline yielded {} page groups", groups.size());
                return groups;
            }
        }

        log.debug("No usable PDF outline — treating all {} pages as a single group", totalPages);
        return List.of(new PageGroup(0, totalPages - 1, null));
    }

    private static List<PageGroup> extractGroupsFromOutline(PDDocument document, PDDocumentOutline outline, int totalPages) {
        List<Integer> pageIndices = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        PDOutlineItem item = outline.getFirstChild();
        while (item != null) {
            try {
                int pageIndex = resolvePageIndex(document, item);
                if (pageIndex >= 0) {
                    pageIndices.add(pageIndex);
                    titles.add(item.getTitle());
                }
            } catch (Exception e) {
                log.debug("Skipping outline item '{}': {}", item.getTitle(), e.getMessage());
            }
            item = item.getNextSibling();
        }

        if (pageIndices.isEmpty()) return List.of();

        // Sort by page index to handle out-of-order bookmarks
        for (int i = 1; i < pageIndices.size(); i++) {
            int keyPage = pageIndices.get(i);
            String keyTitle = titles.get(i);
            int j = i - 1;
            while (j >= 0 && pageIndices.get(j) > keyPage) {
                pageIndices.set(j + 1, pageIndices.get(j));
                titles.set(j + 1, titles.get(j));
                j--;
            }
            pageIndices.set(j + 1, keyPage);
            titles.set(j + 1, keyTitle);
        }

        List<PageGroup> groups = new ArrayList<>();
        for (int i = 0; i < pageIndices.size(); i++) {
            int firstPage = pageIndices.get(i);
            int lastPage = (i + 1 < pageIndices.size()) ? pageIndices.get(i + 1) - 1 : totalPages - 1;
            groups.add(new PageGroup(firstPage, lastPage, titles.get(i)));
        }
        return groups;
    }

    private static int resolvePageIndex(PDDocument document, PDOutlineItem item) throws IOException {
        PDDestination destination = item.getDestination();
        if (destination == null && item.getAction() instanceof PDActionGoTo goTo) {
            destination = goTo.getDestination();
        }
        if (destination instanceof PDPageDestination pageDestination) {
            PDPage page = pageDestination.getPage();
            if (page != null) {
                return document.getPages().indexOf(page);
            }
            int pageNum = pageDestination.getPageNumber();
            if (pageNum >= 0) return pageNum;
        }
        return -1;
    }

    /**
     * Level 2 (digital PDFs): extract raw text from the left 20% of a page.
     * Returns empty string when extraction fails or the page has no selectable text.
     */
    public static String extractTextFromLeftMargin(PDDocument document, int pageIndex) {
        try {
            PDPage page = document.getPage(pageIndex);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();

            Rectangle2D region = new Rectangle2D.Float(0, 0, width * 0.20f, height);

            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.addRegion("left", region);
            stripper.extractRegions(page);

            return stripper.getTextForRegion("left");
        } catch (IOException e) {
            log.warn("Text extraction failed for page {}: {}", pageIndex, e.getMessage());
            return "";
        }
    }
}
