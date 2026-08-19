package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.entity.VendorEntity;
import com.example.salesmgmt.repository.VendorRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;

@Service
public class SingleVendorStatementWorkbookService {

    private final StatementWorkbookV2Service statementWorkbookService;
    private final VendorRepository vendorRepository;

    public SingleVendorStatementWorkbookService(
            StatementWorkbookV2Service statementWorkbookService,
            VendorRepository vendorRepository
    ) {
        this.statementWorkbookService = statementWorkbookService;
        this.vendorRepository = vendorRepository;
    }

    @Transactional(readOnly = true)
    public StatementWorkbookResult generate(
            Long vendorId,
            YearMonth month
    ) {
        VendorEntity vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("거래처를 찾을 수 없습니다."));

        String statementName = normalize(vendor.getStatementName());
        if (statementName.isBlank()) {
            throw new IllegalArgumentException("이 거래처의 명세서명이 등록되어 있지 않습니다.");
        }

        // 빈 달도 거래처 양식 자체는 받을 수 있게 전체 시트를 만든 뒤 해당 거래처 시트만 남긴다.
        StatementWorkbookResult base = statementWorkbookService.generate(
                null,
                month,
                true
        );

        try (
                XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(base.fileBytes())
                );
                ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            boolean found = false;

            for (int index = workbook.getNumberOfSheets() - 1; index >= 0; index--) {
                String current = normalize(workbook.getSheetName(index));
                if (statementName.equals(current)) {
                    found = true;
                } else {
                    workbook.removeSheetAt(index);
                }
            }

            if (!found || workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException(
                        statementName + " 거래처 명세서 시트를 만들지 못했습니다."
                );
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(output);

            String filename = month.getYear()
                    + "년_"
                    + String.format("%02d", month.getMonthValue())
                    + "월_"
                    + safeFilename(statementName)
                    + "_명세서.xlsx";

            return new StatementWorkbookResult(
                    output.toByteArray(),
                    filename,
                    1,
                    1,
                    0,
                    0
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    statementName + " 거래처 명세서 파일을 만드는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeFilename(String value) {
        String safe = value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isBlank() ? "거래처" : safe;
    }
}
