package com.example.salesmgmt.service;

import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DatabaseBackupService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DatabaseBackupService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public byte[] createBackupZip() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("createdAt", LocalDateTime.now().toString());

        Map<String, List<Map<String, Object>>> tables =
                new LinkedHashMap<>();

        List<String> tableNames = jdbcTemplate.query(
                "SHOW TABLES",
                (rs, rowNum) -> rs.getString(1)
        );

        for (String table : tableNames) {
            String quoted = "`" + table.replace("`", "``") + "`";

            List<Map<String, Object>> rows = jdbcTemplate.query(
                    "SELECT * FROM " + quoted,
                    (rs, rowNum) -> rowToMap(rs)
            );

            tables.put(table, rows);
        }

        root.put("tables", tables);

        try {
            byte[] json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(root);

            ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry("backup.json"));
                zip.write(json);
                zip.closeEntry();

                zip.putNextEntry(new ZipEntry("README.txt"));
                zip.write(("""
                        콩나물 관리 시스템 논리 백업
                        생성일: %s

                        backup.json에는 현재 MySQL의 모든 사용자 테이블과 행이 들어 있습니다.
                        잘못된 장부 업로드 1건 복구는 사이트의 '업로드 이력·복구' 기능을 사용하세요.
                        이 ZIP은 장기 보관용 전체 데이터 백업입니다.
                        """.formatted(
                        LocalDateTime.now().format(
                                DateTimeFormatter.ofPattern(
                                        "yyyy-MM-dd HH:mm:ss"
                                )
                        )
                )).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }

            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "DB 백업 파일을 만들지 못했습니다.",
                    exception
            );
        }
    }

    private Map<String, Object> rowToMap(ResultSet rs)
            throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();

        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object value = rs.getObject(i);

            if (value instanceof Timestamp timestamp) {
                value = timestamp.toLocalDateTime().toString();
            } else if (value instanceof java.sql.Date date) {
                value = date.toLocalDate().toString();
            } else if (value instanceof byte[] bytes) {
                value = Base64.getEncoder().encodeToString(bytes);
            }

            row.put(meta.getColumnLabel(i), value);
        }

        return row;
    }
}
