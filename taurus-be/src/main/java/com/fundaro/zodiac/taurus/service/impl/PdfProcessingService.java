package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.config.ApplicationProperties.OcrProperties;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrClient;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.PageImage;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.TextLine;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.TextPage;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrDtos.TextRecognitionResponse;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrException;
import com.fundaro.zodiac.taurus.utils.pdf.PageGroup;
import com.fundaro.zodiac.taurus.utils.pdf.PdfStructureAnalyzer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PdfProcessingService {

    /** A line read on a scanned page counts when its center falls in the left 20% of the page... */
    static final double LEFT_ZONE = 0.20;

    /** ...or in the top 15%, where band parts write the name of the instrument. */
    static final double TOP_ZONE = 0.15;

    /** Largest body of a text reading request, with a margin under the 25 MB accepted by the OMR service. */
    static final long MAX_REQUEST_BYTES = 20L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(PdfProcessingService.class);

    private final MediaService mediaService;
    private final InstrumentsService instrumentsService;
    private final OmrClient omrClient;
    private final ApplicationProperties applicationProperties;

    public PdfProcessingService(
        MediaService mediaService,
        InstrumentsService instrumentsService,
        OmrClient omrClient,
        ApplicationProperties applicationProperties
    ) {
        this.mediaService = mediaService;
        this.instrumentsService = instrumentsService;
        this.omrClient = omrClient;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Converts a list of page images into SheetsMusicDTOs grouped by PDF structure.
     * For each group, attempts instrument detection via PDF text extraction (Level 1); the groups without useful
     * lines have the first page read by the OMR service (Level 2, optional). Sets needsReview=true on DTOs that
     * have auto-suggested instruments.
     */
    public List<SheetsMusicDTO> buildSheets(
        byte[] pdfBytes,
        List<String> imagePaths,
        TracksDTO track,
        AbstractAuthenticationToken token
    ) {
        if (imagePaths.isEmpty()) return List.of();

        List<PageGroup> groups;
        Map<Integer, String> textByFirstPage = new HashMap<>();

        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            groups = PdfStructureAnalyzer.analyzePageGroups(doc);
            for (PageGroup group : groups) {
                String text = PdfStructureAnalyzer.extractTextFromLeftMargin(doc, group.getFirstPage());
                textByFirstPage.put(group.getFirstPage(), text);
            }
        } catch (IOException e) {
            log.error("Failed to analyze PDF structure, falling back to single group: {}", e.getMessage());
            groups = List.of(new PageGroup(0, imagePaths.size() - 1, null));
        }

        List<InstrumentsDTO> allInstruments = loadAllInstruments(token);
        List<Part> parts = new ArrayList<>();

        for (PageGroup group : groups) {
            Set<ChildrenEntitiesDTO> mediaSet = buildMediaSet(group, imagePaths, track, token);
            if (mediaSet.isEmpty()) continue;
            List<String> candidates = parseCandidateLines(textByFirstPage.getOrDefault(group.getFirstPage(), ""));
            parts.add(new Part(group, mediaSet, candidates, candidates.isEmpty() ? firstPage(group, imagePaths) : null));
        }

        List<List<String>> candidates = parts.stream().map(Part::candidates).collect(Collectors.toCollection(ArrayList::new));
        List<Integer> scanned = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).scan() != null) scanned.add(i);
        }
        List<List<String>> read = readScannedPages(scanned.stream().map(i -> parts.get(i).scan()).toList());
        for (int i = 0; i < scanned.size(); i++) {
            candidates.set(scanned.get(i), read.get(i));
        }

        List<SheetsMusicDTO> result = new ArrayList<>();
        long scoreOrder = 0L;
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            Set<ChildrenEntitiesDTO> suggested = matchInstruments(candidates.get(i), allInstruments);

            SheetsMusicDTO dto = new SheetsMusicDTO();
            dto.setOrder(++scoreOrder);
            dto.setDescription(part.group().getTitle());
            dto.setMedia(part.media());
            if (!suggested.isEmpty()) {
                dto.setInstruments(suggested);
                dto.setNeedsReview(true);
            }
            result.add(dto);
        }

        return result;
    }

    /** A group of pages that becomes a part; {@code scan} is the page to read when the PDF gave no useful line. */
    private record Part(PageGroup group, Set<ChildrenEntitiesDTO> media, List<String> candidates, Path scan) {}

    /** The first page of the group not excluded by the user, or {@code null}. */
    private static Path firstPage(PageGroup group, List<String> imagePaths) {
        for (int i = group.getFirstPage(); i <= group.getLastPage() && i < imagePaths.size(); i++) {
            if (imagePaths.get(i) != null) return Path.of(imagePaths.get(i));
        }
        return null;
    }

    /**
     * The candidate lines of each page, in the same order, read by the OMR service in requests within its limits. A
     * page that cannot be read gets no candidates. Without the service, the next requests are not attempted.
     */
    private List<List<String>> readScannedPages(List<Path> pages) {
        List<List<String>> result = new ArrayList<>(Collections.nCopies(pages.size(), List.<String>of()));
        OcrProperties ocr = applicationProperties.getPdfProcessing().getOcr();
        if (pages.isEmpty() || !ocr.isEnabled() || !omrClient.isConfigured()) return result;

        RuntimeException failure = null;
        for (List<Integer> batch : batches(pages, ocr.getPagesPerRequest())) {
            try {
                read(pages, batch, ocr, result);
            } catch (RuntimeException exception) {
                failure = exception;
                // The service is down, busy, slow or refuses the key: the other requests would fail the same way.
                if (!(exception instanceof OmrException omr) || omr.isUnavailable()) break;
            }
        }
        if (failure != null) {
            log.warn("Text reading of the scanned pages not available ({}): their parts get no suggested instruments", failure.getMessage());
        }
        return result;
    }

    /** Indexes of the pages grouped in requests of at most {@code pagesPerRequest} pages and {@link #MAX_REQUEST_BYTES}. */
    private static List<List<Integer>> batches(List<Path> pages, int pagesPerRequest) {
        List<List<Integer>> batches = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        long bytes = 0;
        for (int i = 0; i < pages.size(); i++) {
            long size = sizeOf(pages.get(i));
            if (!current.isEmpty() && (current.size() >= pagesPerRequest || bytes + size > MAX_REQUEST_BYTES)) {
                batches.add(current);
                current = new ArrayList<>();
                bytes = 0;
            }
            current.add(i);
            bytes += size;
        }
        if (!current.isEmpty()) batches.add(current);
        return batches;
    }

    private static long sizeOf(Path page) {
        try {
            return Files.size(page);
        } catch (IOException e) {
            return 0;
        }
    }

    private void read(List<Path> pages, List<Integer> batch, OcrProperties ocr, List<List<String>> result) {
        List<Integer> sent = new ArrayList<>();
        List<PageImage> images = new ArrayList<>();
        for (int index : batch) {
            try {
                // Generic names: the service gets the images only, never the file, the track or the tenant.
                images.add(new PageImage("page-" + (images.size() + 1) + ".png", "image/png", Files.readAllBytes(pages.get(index))));
                sent.add(index);
            } catch (IOException e) {
                log.warn("Scanned page {} not readable: {}", pages.get(index).getFileName(), e.getMessage());
            }
        }
        if (images.isEmpty()) return;
        TextRecognitionResponse response = omrClient.readText(images);
        for (TextPage page : Objects.requireNonNullElse(response.pages(), List.<TextPage>of())) {
            int position = page.page() - 1;
            if (position < 0 || position >= sent.size()) continue;
            result.set(sent.get(position), candidateLines(page.lines(), ocr.getMinConfidence()));
        }
    }

    /** The useful lines in the read zone with at least the minimum confidence. */
    private List<String> candidateLines(List<TextLine> lines, double minConfidence) {
        if (lines == null) return List.of();
        String text = lines
            .stream()
            .filter(line -> line != null && line.text() != null)
            .filter(line -> line.confidence() >= minConfidence)
            .filter(PdfProcessingService::inReadZone)
            .map(TextLine::text)
            .collect(Collectors.joining("\n"));
        log.debug("OCR kept from {} lines: {}", lines.size(), text);
        return parseCandidateLines(text);
    }

    static boolean inReadZone(TextLine line) {
        return line.x() + line.width() / 2 <= LEFT_ZONE || line.y() + line.height() / 2 <= TOP_ZONE;
    }

    private Set<ChildrenEntitiesDTO> buildMediaSet(
        PageGroup group,
        List<String> imagePaths,
        TracksDTO track,
        AbstractAuthenticationToken token
    ) {
        Set<ChildrenEntitiesDTO> mediaSet = new HashSet<>();
        long pageOrder = 1L;
        for (int i = group.getFirstPage(); i <= group.getLastPage() && i < imagePaths.size(); i++) {
            if (imagePaths.get(i) == null) continue; // page was excluded by user annotations
            MediaDTO saved = saveMedia(imagePaths.get(i), track, token);
            if (saved != null) {
                ChildrenEntitiesDTO child = new ChildrenEntitiesDTO();
                child.setIndex(saved.getId());
                child.setName(saved.getName());
                child.setOrder(pageOrder++);
                mediaSet.add(child);
            }
        }
        return mediaSet;
    }

    private MediaDTO saveMedia(String filePath, TracksDTO track, AbstractAuthenticationToken token) {
        try {
            Path path = Path.of(filePath);
            String originalFilename = track.getName() + "-" + path.getFileName();
            return mediaService.store(Files.readAllBytes(path), originalFilename, "image/png", "scores", token);
        } catch (Exception e) {
            log.error("Failed to save media for path {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private List<InstrumentsDTO> loadAllInstruments(AbstractAuthenticationToken token) {
        try {
            return instrumentsService
                .findEntitiesByCriteria(new InstrumentsCriteria(), Pageable.ofSize(1000), token)
                .getContent();
        } catch (Exception e) {
            log.warn("Could not load instruments for matching: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseCandidateLines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("\\r?\\n"))
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> line.length() >= 3)
            .filter(line -> !line.matches("^[\\d\\s.,;:]+$"))
            .filter(line -> !line.matches("^[IVXivx]+\\.?$"))
            .distinct()
            .collect(Collectors.toList());
    }

    private Set<ChildrenEntitiesDTO> matchInstruments(List<String> candidates, List<InstrumentsDTO> instruments) {
        Set<ChildrenEntitiesDTO> matched = new HashSet<>();
        for (String candidate : candidates) {
            InstrumentsDTO instrument = bestMatch(candidate, instruments);
            if (instrument != null) {
                ChildrenEntitiesDTO child = new ChildrenEntitiesDTO();
                child.setIndex(instrument.getId());
                child.setName(instrument.getName());
                child.setOrder((long) (matched.size() + 1));
                matched.add(child);
            }
        }
        return matched;
    }

    /**
     * The instrument a line names, comparing whole words: the same name first, then the longest name found in the line,
     * then the first name that holds the whole line. "Corno in Mib" is then "Corno 1°", never "Flicorno soprano 1°".
     */
    static InstrumentsDTO bestMatch(String candidate, List<InstrumentsDTO> instruments) {
        List<String> line = words(candidate);
        if (line.isEmpty()) return null;
        InstrumentsDTO inLine = null;
        int inLineWords = 0;
        InstrumentsDTO holdingLine = null;
        for (InstrumentsDTO instrument : instruments) {
            List<String> name = words(instrument.getName());
            if (name.isEmpty()) continue;
            if (name.equals(line)) return instrument;
            if (name.size() > inLineWords && Collections.indexOfSubList(line, name) >= 0) {
                inLine = instrument;
                inLineWords = name.size();
            }
            if (holdingLine == null && Collections.indexOfSubList(name, line) >= 0) {
                holdingLine = instrument;
            }
        }
        return inLine != null ? inLine : holdingLine;
    }

    private static List<String> words(String s) {
        String normalized = normalize(s);
        if (normalized.isEmpty()) return List.of();
        return Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+")).filter(word -> !word.isEmpty()).toList();
    }

    /** Lower case without accents, and without the key ("in Sib"), the roman or arabic number ("1°") at the end. */
    private static String normalize(String s) {
        if (s == null) return "";
        String result = Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "").trim();
        String previous;
        do {
            previous = result;
            result = result
                .replaceAll("\\s+in\\s+\\w+$", "")
                .replaceAll("\\s+[ivxlcdm]+$", "")
                .replaceAll("\\s+\\d+\\s*[°ºª]?$", "")
                .trim();
        } while (!result.equals(previous));
        return result;
    }
}
