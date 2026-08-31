package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.criteria.InstrumentsCriteria;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.utils.pdf.PageGroup;
import com.fundaro.zodiac.taurus.utils.pdf.PdfStructureAnalyzer;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PdfProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PdfProcessingService.class);

    private final MediaService mediaService;
    private final InstrumentsService instrumentsService;
    private final ApplicationProperties applicationProperties;

    public PdfProcessingService(
        MediaService mediaService,
        InstrumentsService instrumentsService,
        ApplicationProperties applicationProperties
    ) {
        this.mediaService = mediaService;
        this.instrumentsService = instrumentsService;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Converts a list of page images into SheetsMusicDTOs grouped by PDF structure.
     * For each group, attempts instrument detection via PDF text extraction (Level 1)
     * and Tesseract OCR (Level 2, optional). Sets needsReview=true on DTOs that
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
        List<SheetsMusicDTO> result = new ArrayList<>();
        long scoreOrder = 0L;

        for (PageGroup group : groups) {
            Set<ChildrenEntitiesDTO> mediaSet = buildMediaSet(group, imagePaths, track, token);
            if (mediaSet.isEmpty()) continue;

            List<String> candidates = parseCandidateLines(textByFirstPage.getOrDefault(group.getFirstPage(), ""));

            if (candidates.isEmpty() && applicationProperties.getTesseract().isEnabled()) {
                String firstNonNullPath = null;
                for (int i = group.getFirstPage(); i <= group.getLastPage() && i < imagePaths.size(); i++) {
                    if (imagePaths.get(i) != null) {
                        firstNonNullPath = imagePaths.get(i);
                        break;
                    }
                }
                if (firstNonNullPath != null) {
                    candidates = extractViaOcr(firstNonNullPath);
                }
            }

            Set<ChildrenEntitiesDTO> suggested = matchInstruments(candidates, allInstruments);

            SheetsMusicDTO dto = new SheetsMusicDTO();
            dto.setOrder(++scoreOrder);
            dto.setDescription(group.getTitle());
            dto.setMedia(mediaSet);
            if (!suggested.isEmpty()) {
                dto.setInstruments(suggested);
                dto.setNeedsReview(true);
            }
            result.add(dto);
        }

        return result;
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

    private List<String> extractViaOcr(String imagePath) {
        ApplicationProperties.TesseractProperties cfg = applicationProperties.getTesseract();
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(cfg.getDataPath());
            tesseract.setLanguage(cfg.getLanguage());
            String text = tesseract.doOCR(new File(imagePath));
            log.debug("OCR extracted {} chars from {}", text.length(), imagePath);
            return parseCandidateLines(text);
        } catch (TesseractException e) {
            log.warn("OCR failed for {}: {}", imagePath, e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            log.error("Tesseract native library not found — install Tesseract and ensure it is on the library path");
        }
        return List.of();
    }

    private Set<ChildrenEntitiesDTO> matchInstruments(List<String> candidates, List<InstrumentsDTO> instruments) {
        Set<ChildrenEntitiesDTO> matched = new HashSet<>();
        for (String candidate : candidates) {
            String normalizedCandidate = normalize(candidate);
            if (normalizedCandidate.isBlank()) continue;
            for (InstrumentsDTO instrument : instruments) {
                String normalizedName = normalize(instrument.getName());
                if (!normalizedName.isBlank()
                    && (normalizedCandidate.contains(normalizedName) || normalizedName.contains(normalizedCandidate))
                ) {
                    ChildrenEntitiesDTO child = new ChildrenEntitiesDTO();
                    child.setIndex(instrument.getId());
                    child.setName(instrument.getName());
                    child.setOrder((long) (matched.size() + 1));
                    matched.add(child);
                    break;
                }
            }
        }
        return matched;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String result = s.toLowerCase();
        result = Normalizer.normalize(result, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        result = result.replaceAll("\\s+[ivxlcdm]+$", "");
        result = result.replaceAll("\\s+in\\s+\\w+$", "");
        result = result.replaceAll("\\s+\\d+$", "");
        return result.trim();
    }
}
