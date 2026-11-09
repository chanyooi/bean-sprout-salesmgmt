package com.example.salesmgmt.service;

import com.example.salesmgmt.domain.StandardStatementSchema;
import com.example.salesmgmt.domain.StandardStatementSchema.Kind;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StandardStatementSchemaTest {

    @Test
    void containsEveryAccountCodeFromThree2025Forms() {
        assertEquals(91, StandardStatementSchema.fields(Kind.BALANCE).size());
        assertEquals(99, StandardStatementSchema.fields(Kind.INCOME).size());
        assertEquals(42, StandardStatementSchema.fields(Kind.COST).size());
        for (Kind kind : Kind.values()) {
            int count = 1;
            for (StandardStatementSchema.Field field : StandardStatementSchema.fields(kind))
                assertEquals(String.format("%02d", count++), field.code());
        }
    }

    @Test
    void reconciles2025SongcheonFiledFiguresAcrossAllThreeForms() {
        Map<String, BigDecimal> inputs = new HashMap<>();
        // 2025년 실제 제출 표준원가명세서 숫자 (총 경비 76,871,403)
        set(inputs, Kind.COST, "03", 321716000);
        set(inputs, Kind.COST, "08", 19040000);
        set(inputs, Kind.COST, "12", 18364710);
        set(inputs, Kind.COST, "13", 484590);
        set(inputs, Kind.COST, "17", 176000);
        set(inputs, Kind.COST, "18", 14931632);
        set(inputs, Kind.COST, "19", 178430);
        set(inputs, Kind.COST, "20", 18000000);
        set(inputs, Kind.COST, "21", 8517876);
        set(inputs, Kind.COST, "22", 2370620);
        set(inputs, Kind.COST, "26", 13847545);

        // 표준손익계산서 2025
        set(inputs, Kind.INCOME, "03", 485365400);
        set(inputs, Kind.INCOME, "28", 813718);
        set(inputs, Kind.INCOME, "41", 9956900);
        set(inputs, Kind.INCOME, "47", 3856200);
        set(inputs, Kind.INCOME, "51", 9874780);

        // 표준재무상태표 2025
        set(inputs, Kind.BALANCE, "03", 559333);
        set(inputs, Kind.BALANCE, "10", 27006100);
        set(inputs, Kind.BALANCE, "16", 1134116);
        set(inputs, Kind.BALANCE, "42", 1000);
        set(inputs, Kind.BALANCE, "45", 3000);
        set(inputs, Kind.BALANCE, "49", 1000);
        set(inputs, Kind.BALANCE, "59", 50000000);
        set(inputs, Kind.BALANCE, "88", 35468150);

        Map<String, BigDecimal> result = StandardStatementSchema.evaluate(inputs);
        assertEquals(new BigDecimal("76871403"), result.get("COST:11"));
        assertEquals(new BigDecimal("417627403"), result.get("COST:42"));
        assertEquals(result.get("COST:42"), result.get("INCOME:17"));
        assertEquals(new BigDecimal("24501598"), result.get("INCOME:21"));
        assertEquals(new BigDecimal("43236399"), result.get("INCOME:99"));
        assertEquals(new BigDecimal("78704549"), result.get("BALANCE:62"));
        assertEquals(result.get("INCOME:99"), result.get("BALANCE:89"));
        assertEquals(result.get("BALANCE:62"), result.get("BALANCE:91"));
    }

    @Test
    void inventoryAdjustmentsReduceMaterialExpenseWithoutAffectingPurchases() {
        Map<String, BigDecimal> inputs = new HashMap<>();
        set(inputs, Kind.COST, "02", 100000);
        set(inputs, Kind.COST, "03", 1000000);
        set(inputs, Kind.COST, "04", 200000);
        assertEquals(new BigDecimal("900000"),
                StandardStatementSchema.evaluate(inputs).get("COST:01"));
    }

    private static void set(Map<String, BigDecimal> values, Kind type, String code, long amount) {
        values.put(StandardStatementSchema.key(type, code), BigDecimal.valueOf(amount));
    }
}
