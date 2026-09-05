package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.*;
import com.fundaro.zodiac.taurus.repository.onboarding.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingReportService {
    private final OnboardingImportRowRepository rows;
    private final OnboardingImportIssueRepository issues;
    public OnboardingReportService(OnboardingImportRowRepository rows, OnboardingImportIssueRepository issues) { this.rows = rows; this.issues = issues; }

    @Transactional(readOnly = true)
    public byte[] report(Long jobId) {
        List<OnboardingImportRow> staged = rows.findAllByJob_IdOrderBySectionAscRowNumberAsc(jobId);
        Map<Long, List<OnboardingImportIssue>> byRow = issues.findAllByJob_Id(jobId, Pageable.unpaged()).stream().filter(i -> i.getRow() != null).collect(Collectors.groupingBy(i -> i.getRow().getId()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (OnboardingSection section : OnboardingSection.values()) {
                List<OnboardingImportRow> scoped = staged.stream().filter(row -> row.getSection() == section).toList(); if (scoped.isEmpty()) continue;
                Sheet sheet = workbook.createSheet(section.getSheetName()); Row header = sheet.createRow(0); int column = 0;
                for (String name : section.getHeaders()) header.createCell(column++).setCellValue(name);
                header.createCell(column++).setCellValue("esito"); header.createCell(column++).setCellValue("codici_problema"); header.createCell(column).setCellValue("messaggi"); sheet.createFreezePane(0, 1);
                int index = 1; for (OnboardingImportRow stagedRow : scoped) { Row target = sheet.createRow(index++); int c = 0; for (String name : section.getHeaders()) target.createCell(c++).setCellValue(safe(Objects.toString(stagedRow.getNormalizedPayload().get(name), "")));
                    List<OnboardingImportIssue> rowIssues = byRow.getOrDefault(stagedRow.getId(), List.of()); target.createCell(c++).setCellValue(stagedRow.getStatus().name()); target.createCell(c++).setCellValue(rowIssues.stream().map(OnboardingImportIssue::getCode).distinct().collect(Collectors.joining("|"))); target.createCell(c).setCellValue(rowIssues.stream().map(OnboardingImportIssue::getMessage).collect(Collectors.joining(" | "))); }
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, column));
            }
            if (workbook.getNumberOfSheets() == 0) workbook.createSheet("Esito").createRow(0).createCell(0).setCellValue("Nessuna riga disponibile");
            workbook.write(output); return output.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("Unable to generate onboarding report", exception); }
    }
    static String safe(String value) { if (value != null && !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) return "'" + value; return value; }
}
