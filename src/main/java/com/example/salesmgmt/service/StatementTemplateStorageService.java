package com.example.salesmgmt.service;

import com.example.salesmgmt.entity.StatementTemplateEntity;
import com.example.salesmgmt.repository.StatementTemplateRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class StatementTemplateStorageService {

    private static final Long ACTIVE_TEMPLATE_ID = 1L;
    private static final String DEFAULT_TEMPLATE_PATH = "template.xlsx";

    private final StatementTemplateRepository repository;
    private final DataFormatter formatter = new DataFormatter(Locale.KOREA);

    public StatementTemplateStorageService(StatementTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public MultipartFile resolveAndSaveIfUploaded(MultipartFile uploadedFile) {
        if (uploadedFile != null && !uploadedFile.isEmpty()) {
            StoredTemplate template = validate(uploadedFile);
            save(template.filename(), template.bytes());
            return new ByteArrayMultipartFile(template.filename(), template.bytes());
        }

        return repository.findById(ACTIVE_TEMPLATE_ID)
                .map(entity -> (MultipartFile) new ByteArrayMultipartFile(
                        entity.getOriginalFilename(),
                        entity.getFileBytes()
                ))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public InputStream openCurrentTemplate() throws IOException {
        var stored = repository.findById(ACTIVE_TEMPLATE_ID);
        if (stored.isPresent()) {
            return new ByteArrayInputStream(stored.get().getFileBytes());
        }

        ClassPathResource resource = new ClassPathResource(DEFAULT_TEMPLATE_PATH);
        if (!resource.exists()) {
            throw new IllegalArgumentException("기본 template.xlsx 파일을 찾을 수 없습니다.");
        }
        return resource.getInputStream();
    }

    @Transactional(readOnly = true)
    public String currentFilename() {
        return repository.findById(ACTIVE_TEMPLATE_ID)
                .map(StatementTemplateEntity::getOriginalFilename)
                .orElse("template.xlsx");
    }

    @Transactional(readOnly = true)
    public LocalDateTime currentUpdatedAt() {
        return repository.findById(ACTIVE_TEMPLATE_ID)
                .map(StatementTemplateEntity::getUpdatedAt)
                .orElse(null);
    }

    private void save(String filename, byte[] bytes) {
        StatementTemplateEntity entity = repository.findById(ACTIVE_TEMPLATE_ID)
                .orElseGet(() -> new StatementTemplateEntity(
                        ACTIVE_TEMPLATE_ID,
                        filename,
                        bytes
                ));

        if (repository.existsById(ACTIVE_TEMPLATE_ID)) {
            entity.update(filename, bytes);
        }
        repository.save(entity);
    }

    private StoredTemplate validate(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException(".xlsx 형식의 템플릿만 사용할 수 있습니다.");
        }

        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new IllegalArgumentException("빈 템플릿 파일은 사용할 수 없습니다.");
            }

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                int validSheets = 0;
                for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                    XSSFSheet sheet = workbook.getSheetAt(index);
                    if (sheet.getSheetName().startsWith("생성확인")) {
                        continue;
                    }
                    validateSheet(sheet);
                    validSheets++;
                }
                if (validSheets == 0) {
                    throw new IllegalArgumentException("거래명세서 양식 시트를 찾지 못했습니다.");
                }
            }

            return new StoredTemplate(filename, bytes);
        } catch (IOException exception) {
            throw new IllegalArgumentException("엑셀 템플릿 파일을 읽을 수 없습니다.", exception);
        }
    }

    private void validateSheet(XSSFSheet sheet) {
        int headerRow = -1;
        int scanLimit = Math.min(sheet.getLastRowNum(), 50);
        for (int rowIndex = 0; rowIndex <= scanLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && "날짜".equals(formatted(row.getCell(0)))) {
                headerRow = rowIndex;
                break;
            }
        }
        if (headerRow < 0) {
            throw new IllegalArgumentException(
                    sheet.getSheetName() + " 시트에서 '날짜' 헤더를 찾지 못했습니다."
            );
        }

        int sumLimit = Math.min(sheet.getLastRowNum(), headerRow + 41);
        for (int rowIndex = headerRow + 1; rowIndex <= sumLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && "합계".equals(formatted(row.getCell(0)))) {
                int dataRows = rowIndex - headerRow - 1;
                if (dataRows < 31) {
                    throw new IllegalArgumentException(
                            sheet.getSheetName() + " 시트의 날짜 입력 행이 31행보다 적습니다."
                    );
                }
                return;
            }
        }

        throw new IllegalArgumentException(
                sheet.getSheetName() + " 시트에서 '합계' 행을 찾지 못했습니다."
        );
    }

    private String formatted(org.apache.poi.ss.usermodel.Cell cell) {
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private record StoredTemplate(String filename, byte[] bytes) {
    }

    private static final class ByteArrayMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] bytes;

        private ByteArrayMultipartFile(String filename, byte[] bytes) {
            this.filename = filename;
            this.bytes = bytes;
        }

        @Override
        public String getName() {
            return "templateFile";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            java.nio.file.Files.write(dest.toPath(), bytes);
        }
    }
}
