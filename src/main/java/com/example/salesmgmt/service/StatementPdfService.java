package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StatementWorkbookResult;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class StatementPdfService {

    private static final long CONVERSION_TIMEOUT_SECONDS = 120;

    private final StatementWorkbookService statementWorkbookService;

    public StatementPdfService(
            StatementWorkbookService statementWorkbookService
    ) {
        this.statementWorkbookService = statementWorkbookService;
    }

    public PdfResult generate(
            MultipartFile templateFile,
            YearMonth month,
            boolean includeEmptySheets
    ) {
        StatementWorkbookResult workbookResult =
                statementWorkbookService.generate(
                        templateFile,
                        month,
                        includeEmptySheets
                );

        Path workDir = null;

        try {
            workDir = Files.createTempDirectory("statement-pdf-");

            Path sourceXlsx = workDir.resolve("statement-source.xlsx");
            Path outputPdf = workDir.resolve("statement-source.pdf");

            Files.write(
                    sourceXlsx,
                    prepareWorkbookForPdf(workbookResult.fileBytes())
            );

            convertWithLibreOffice(sourceXlsx, workDir);

            if (!Files.exists(outputPdf)) {
                throw new IllegalStateException(
                        "PDF 변환은 실행됐지만 결과 PDF 파일을 찾지 못했습니다."
                );
            }

            String filename = month.getYear()
                    + "년_"
                    + String.format("%02d", month.getMonthValue())
                    + "월_거래명세서_템플릿.pdf";

            return new PdfResult(
                    Files.readAllBytes(outputPdf),
                    filename
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "거래명세서 PDF 파일을 만드는 중 오류가 발생했습니다: "
                            + exception.getMessage(),
                    exception
            );
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * 엑셀에만 필요한 생성확인 시트는 PDF에서 제외하고,
     * 거래처 시트는 A4 한 페이지에 맞춰 출력되도록 설정합니다.
     */
    private byte[] prepareWorkbookForPdf(byte[] sourceBytes) throws IOException {
        try (
                XSSFWorkbook workbook = new XSSFWorkbook(
                        new ByteArrayInputStream(sourceBytes)
                );
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()
        ) {
            for (int index = workbook.getNumberOfSheets() - 1;
                 index >= 0;
                 index--) {
                String sheetName = workbook.getSheetName(index);
                if (sheetName != null && sheetName.startsWith("생성확인")) {
                    workbook.removeSheetAt(index);
                }
            }

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalStateException(
                        "PDF로 만들 거래명세서 시트가 없습니다."
                );
            }

            for (int index = 0;
                 index < workbook.getNumberOfSheets();
                 index++) {
                XSSFSheet sheet = workbook.getSheetAt(index);
                sheet.setAutobreaks(true);
                sheet.setFitToPage(true);

                PrintSetup printSetup = sheet.getPrintSetup();
                printSetup.setPaperSize(PrintSetup.A4_PAPERSIZE);
                printSetup.setFitWidth((short) 1);
                printSetup.setFitHeight((short) 1);
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void convertWithLibreOffice(
            Path sourceXlsx,
            Path outputDir
    ) {
        String command = resolveLibreOfficeCommand();
        Path profileDir = outputDir.resolve("lo-profile");

        try {
            Files.createDirectories(profileDir);

            Process process = new ProcessBuilder(
                    command,
                    "--headless",
                    "--nologo",
                    "--nodefault",
                    "--nolockcheck",
                    "--nofirststartwizard",
                    "-env:UserInstallation=" + profileDir.toUri(),
                    "--convert-to",
                    "pdf:calc_pdf_Export",
                    "--outdir",
                    outputDir.toAbsolutePath().toString(),
                    sourceXlsx.toAbsolutePath().toString()
            )
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(
                    CONVERSION_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException(
                        "PDF 변환 시간이 120초를 초과했습니다."
                );
            }

            String processOutput = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "LibreOffice PDF 변환 실패(exit="
                                + process.exitValue()
                                + "): "
                                + processOutput
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "PDF 변환용 LibreOffice를 실행할 수 없습니다. "
                            + "로컬 PC에는 LibreOffice를 설치하고, "
                            + "Railway에는 libreoffice-calc 런타임 패키지를 추가해주세요. "
                            + "원인: "
                            + exception.getMessage(),
                    exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "PDF 변환이 중단되었습니다.",
                    exception
            );
        }
    }

    private String resolveLibreOfficeCommand() {
        String configured = System.getenv("LIBREOFFICE_COMMAND");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }

        String osName = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT);

        if (osName.contains("win")) {
            Path programFiles = Path.of(
                    "C:\\Program Files\\LibreOffice\\program\\soffice.exe"
            );
            if (Files.exists(programFiles)) {
                return programFiles.toString();
            }

            Path programFilesX86 = Path.of(
                    "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe"
            );
            if (Files.exists(programFilesX86)) {
                return programFilesX86.toString();
            }

            return "soffice";
        }

        return "libreoffice";
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 임시파일 정리 실패는 PDF 생성 결과에 영향을 주지 않습니다.
                        }
                    });
        } catch (IOException ignored) {
            // 임시폴더 정리 실패는 무시합니다.
        }
    }

    public record PdfResult(
            byte[] fileBytes,
            String filename
    ) {}
}
