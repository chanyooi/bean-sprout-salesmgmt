package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StandardStatementSchema;
import com.example.salesmgmt.domain.StandardStatementSchema.Kind;
import com.example.salesmgmt.entity.StandardStatementValueEntity;
import com.example.salesmgmt.repository.StandardStatementValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class StandardStatementService {
    private final StandardStatementValueRepository repository;
    private static final BigDecimal LIMIT = new BigDecimal("9999999999999.99");

    public StandardStatementService(StandardStatementValueRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> entered(int year) {
        Map<String, BigDecimal> values = new HashMap<>();
        for (StandardStatementValueEntity row : repository.findByYear(year))
            values.put(row.getType() + ":" + row.getCode(), row.getAmount());
        return values;
    }

    @Transactional
    public void save(int year, Kind kind, Map<String, String> values) {
        List<StandardStatementValueEntity> existing = repository.findByYearAndType(year, kind.name());
        Map<String, StandardStatementValueEntity> byCode = new HashMap<>();
        for (StandardStatementValueEntity entry : existing) byCode.put(entry.getCode(), entry);

        // 먼저 모든 입력의 유효성을 검증한 뒤 한 트랜잭션에서 저장/삭제
        Map<String, BigDecimal> parsed = new LinkedHashMap<>();
        for (StandardStatementSchema.Field field : StandardStatementSchema.fields(kind)) {
            if (field.computed()) continue;
            String raw = values.get("value_" + field.code());
            if (raw == null || raw.isBlank()) {
                parsed.put(field.code(), null);
            } else {
                try {
                    BigDecimal amount = new BigDecimal(raw.trim().replace(",", ""));
                    if (amount.abs().compareTo(LIMIT) > 0 || amount.scale() > 2)
                        throw new NumberFormatException();
                    parsed.put(field.code(), amount);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException(field.label() + " 금액을 확인해주세요. 원 단위 숫자를 입력해야 합니다.");
                }
            }
        }

        List<StandardStatementValueEntity> upserts = new ArrayList<>();
        List<StandardStatementValueEntity> deletes = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> record : parsed.entrySet()) {
            StandardStatementValueEntity row = byCode.get(record.getKey());
            if (record.getValue() == null) {
                if (row != null) deletes.add(row);
            } else if (row == null) {
                upserts.add(new StandardStatementValueEntity(year, kind.name(), record.getKey(), record.getValue()));
            } else {
                row.setAmount(record.getValue());
                upserts.add(row);
            }
        }
        repository.saveAll(upserts);
        repository.deleteAll(deletes);
    }
}
