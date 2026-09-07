package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementDeliveryMethod;
import com.example.salesmgmt.domain.StatementWorkbookResult;
import com.example.salesmgmt.repository.VendorRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;

@Service
public class FilteredStatementWorkbookService {

    private final StatementWorkbookOnePassService statementWorkbookService;
    private final VendorRepository vendorRepository;

    public FilteredStatementWorkbookService(
            StatementWorkbookOnePassService statementWorkbookService,
            VendorRepository vendorRepository
    ) {
        this.statementWorkbookService = statementWorkbookService;
        this.vendorRepository = vendorRepository;
    }

    @Transactional(readOnly = true)
    public StatementWorkbookResult generate(
            MultipartFile templateFile,
            YearMonth month,
            boolean includeEmptySheets,
            StatementDeliveryMethod deliveryMethod
    ) {
        /*
         * 예전에는 StatementWorkbookV2Service를 사용했습니다.
         * V2는 legacy workbook 생성 -> 다시 XSSFWorkbook으로 열기 과정을 거치고,
         * 이 서비스가 그 결과를 또 열어 필터링했기 때문에 Railway의 작은 메모리에서
         * 팩스/우편/문자 다운로드 시 순간 메모리 사용량이 크게 치솟았습니다.
         *
         * 전체 명세서와 동일한 one-pass 생성기를 사용한 뒤 한 번만 필터링하여
         * 동일한 결과를 유지하면서 중간 workbook 한 세트를 없앱니다.
         */
        StatementWorkbookResult base = statementWorkbookService.generate(
                templateFile,
                month,
                includeEmptySheets
        );

        Set<String> allowedStatementNames = new HashSet<>();
        vendorRepository.findAll().stream()
                .filter(vendor -> vendor.getStatementDeliveryMethod() == deliveryMethod)
                .map(vendor -> vendor.getStatementName() == null ? "" : vendor.getStatementName().trim())
                .filter(name -> !name.isBlank())
                .forEach(allowedStatementNames::add);

        if (allowedStatementNames.isEmpty()) {
            throw new IllegalArgumentException(
                    deliveryMethod.getLabel() + "로 분류된 거래처가 없습니다."
            );
        }

        try (
                XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(base.fileBytes())
                );
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            for (int index = workbook.getNumberOfSheets() - 1; index >= 0; index--) {
                String sheetName = workbook.getSheetName(index).trim();
                if (!allowedStatementNames.contains(sheetName)) {
                    workbook.removeSheetAt(index);
                }
            }

            int remainingSheets = workbook.getNumberOfSheets();
            if (remainingSheets == 0) {
                throw new IllegalArgumentException(
                        month + "에 " + deliveryMethod.getLabel()
                                + " 거래처용으로 생성할 명세서가 없습니다."
                );
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(outputStream);

            String filename = month.getYear()
                    + "년_"
                    + String.format("%02d", month.getMonthValue())
                    + "월_"
                    + deliveryMethod.getLabel()
                    + "거래처_명세서.xlsx";

            return new StatementWorkbookResult(
                    outputStream.toByteArray(),
                    filename,
                    remainingSheets,
                    Math.min(base.sheetWithSalesCount(), remainingSheets),
                    base.removedEmptySheetCount(),
                    base.warningCount()
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    deliveryMethod.getLabel()
                            + " 거래처용 명세서 파일을 만드는 중 오류가 발생했습니다.",
                    exception
            );
        }
    }
}
