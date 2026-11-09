package com.example.salesmgmt.controller;

import com.example.salesmgmt.domain.ManualBookkeepingAccounts;
import com.example.salesmgmt.domain.ManualBookkeepingCategory;
import com.example.salesmgmt.entity.ManualBookkeepingMonthlyEntity;
import com.example.salesmgmt.entity.ManualJournalEntryEntity;
import com.example.salesmgmt.repository.ManualBookkeepingMonthlyRepository;
import com.example.salesmgmt.repository.ManualJournalEntryRepository;
import com.example.salesmgmt.service.ManualBookkeepingExcelService;
import com.example.salesmgmt.service.StandardStatementExcelService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/bookkeeping")
public class ManualBookkeepingController {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999.99");

    private final ManualBookkeepingMonthlyRepository monthlyRepository;
    private final ManualJournalEntryRepository journalRepository;
    private final ManualBookkeepingExcelService excelService;
    private final StandardStatementExcelService standardExcelService;

    public ManualBookkeepingController(ManualBookkeepingMonthlyRepository monthlyRepository,
                                       ManualJournalEntryRepository journalRepository,
                                       ManualBookkeepingExcelService excelService,
                                       StandardStatementExcelService standardExcelService) {
        this.monthlyRepository = monthlyRepository;
        this.journalRepository = journalRepository;
        this.excelService = excelService;
        this.standardExcelService = standardExcelService;
    }

    @GetMapping("/access")
    @ResponseBody
    public ResponseEntity<Void> access() {
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monthly-view")
    @Transactional(readOnly = true)
    public String page(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       Model model) {
        LocalDate today = LocalDate.now();
        int selectedYear = year == null ? today.getYear() : year;
        int selectedMonth = month == null ? today.getMonthValue() : month;
        validateYearMonth(selectedYear, selectedMonth);

        List<ManualBookkeepingMonthlyEntity> allMonths =
                monthlyRepository.findByYearOrderByMonthAscCategoryCodeAsc(selectedYear);
        Map<String, BigDecimal> perMonth = new HashMap<>();
        Map<String, BigDecimal> annual = new HashMap<>();
        for (ManualBookkeepingMonthlyEntity entry : allMonths) {
            perMonth.put(entry.getCategoryCode() + ":" + entry.getMonth(), entry.getAmount());
            annual.merge(entry.getCategoryCode(), entry.getAmount(), BigDecimal::add);
        }
        List<CategoryRow> rows = new ArrayList<>();
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal costs = BigDecimal.ZERO;
        for (ManualBookkeepingCategory category : ManualBookkeepingCategory.values()) {
            BigDecimal value = perMonth.getOrDefault(category.name() + ":" + selectedMonth, BigDecimal.ZERO);
            BigDecimal yearTotal = annual.getOrDefault(category.name(), BigDecimal.ZERO);
            rows.add(new CategoryRow(category.name(), category.getLabel(), value, yearTotal));
            if (category.isRevenue()) income = income.add(value);
            else costs = costs.add(value);
        }
        List<ManualJournalEntryEntity> entries = journalRepository
                .findByEntryDateBetweenOrderByEntryDateAscIdAsc(
                        LocalDate.of(selectedYear, selectedMonth, 1),
                        YearMonth.of(selectedYear, selectedMonth).atEndOfMonth());

        LocalDate defaultDate = today.getYear() == selectedYear && today.getMonthValue() == selectedMonth
                ? today : LocalDate.of(selectedYear, selectedMonth, 1);
        model.addAttribute("year", selectedYear);
        model.addAttribute("month", selectedMonth);
        model.addAttribute("rows", rows);
        model.addAttribute("income", income);
        model.addAttribute("costs", costs);
        model.addAttribute("difference", income.subtract(costs));
        model.addAttribute("entries", entries);
        model.addAttribute("accounts", ManualBookkeepingAccounts.ALL);
        model.addAttribute("today", defaultDate);
        return "manual-bookkeeping";
    }

    @PostMapping("/monthly")
    @Transactional
    public String saveMonthly(@RequestParam int year, @RequestParam int month,
                              @RequestParam Map<String, String> params,
                              RedirectAttributes flash) {
        validateYearMonth(year, month);
        try {
            Map<String, BigDecimal> parsed = new LinkedHashMap<>();
            for (ManualBookkeepingCategory category : ManualBookkeepingCategory.values()) {
                parsed.put(category.name(), parseMoney(params.get("amount_" + category.name()), false));
            }
            Map<String, ManualBookkeepingMonthlyEntity> existing =
                    monthlyRepository.findByYearAndMonth(year, month).stream()
                            .collect(Collectors.toMap(ManualBookkeepingMonthlyEntity::getCategoryCode, row -> row));
            List<ManualBookkeepingMonthlyEntity> updated = new ArrayList<>();
            for (Map.Entry<String, BigDecimal> amount : parsed.entrySet()) {
                ManualBookkeepingMonthlyEntity row = existing.get(amount.getKey());
                if (row == null) row = new ManualBookkeepingMonthlyEntity(year, month, amount.getKey(), amount.getValue());
                else row.setAmount(amount.getValue());
                updated.add(row);
            }
            monthlyRepository.saveAll(updated);
            flash.addFlashAttribute("bookkeepingMessage", "월별 금액을 저장했습니다.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("bookkeepingError", ex.getMessage());
        }
        return redirect(year, month);
    }

    @PostMapping("/journal")
    public String saveJournal(@RequestParam int year, @RequestParam int month,
                              @RequestParam String entryDate, @RequestParam String description,
                              @RequestParam String debitAccount, @RequestParam String creditAccount,
                              @RequestParam String amount, RedirectAttributes flash) {
        validateYearMonth(year, month);
        try {
            LocalDate date = LocalDate.parse(entryDate);
            if (date.getYear() != year || date.getMonthValue() != month)
                throw new IllegalArgumentException("선택한 월 안의 거래일을 입력해주세요.");
            if (description == null || description.isBlank() || description.strip().length() > 240)
                throw new IllegalArgumentException("거래 내용은 1~240자로 입력해주세요.");
            if (!ManualBookkeepingAccounts.ALL.contains(debitAccount) ||
                    !ManualBookkeepingAccounts.ALL.contains(creditAccount) ||
                    debitAccount.equals(creditAccount))
                throw new IllegalArgumentException("서로 다른 올바른 차변/대변 계정을 선택해주세요.");
            journalRepository.save(new ManualJournalEntryEntity(date, description.strip(),
                    debitAccount, creditAccount, parseMoney(amount, true)));
            flash.addFlashAttribute("bookkeepingMessage", "차변·대변 분개를 저장했습니다.");
        } catch (RuntimeException ex) {
            flash.addFlashAttribute("bookkeepingError",
                    ex instanceof IllegalArgumentException ? ex.getMessage() : "거래 날짜와 금액을 확인해주세요.");
        }
        return redirect(year, month);
    }

    @PostMapping("/journal/{id}/delete")
    public String deleteJournal(@PathVariable Long id, @RequestParam int year,
                                @RequestParam int month, RedirectAttributes flash) {
        validateYearMonth(year, month);
        Optional<ManualJournalEntryEntity> entry = journalRepository.findById(id);
        if (entry.isPresent() && entry.get().getEntryDate().getYear() == year &&
                entry.get().getEntryDate().getMonthValue() == month) {
            journalRepository.delete(entry.get());
            flash.addFlashAttribute("bookkeepingMessage", "분개를 삭제했습니다.");
        } else {
            flash.addFlashAttribute("bookkeepingError", "선택한 월에 해당하는 분개를 찾지 못했습니다.");
        }
        return redirect(year, month);
    }

    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> export(@RequestParam int year) throws IOException {
        if (year < 2020 || year > 2100)
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "조회 가능한 연도는 2020~2100년입니다.");
        byte[] file = excelService.export(year,
                monthlyRepository.findByYearOrderByMonthAscCategoryCodeAsc(year),
                journalRepository.findByEntryDateBetweenOrderByEntryDateAscIdAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
        file = standardExcelService.addStandardSheets(year, file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"songcheon_bookkeeping_" + year + ".xlsx\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(file.length)
                .body(file);
    }

    private static BigDecimal parseMoney(String raw, boolean positiveOnly) {
        if (raw == null || raw.isBlank()) {
            if (positiveOnly) throw new IllegalArgumentException("거래 금액을 입력해주세요.");
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = new BigDecimal(raw.strip().replace(",", ""));
            if (value.abs().compareTo(MAX_AMOUNT) > 0 || value.scale() > 2 ||
                    (positiveOnly && value.signum() <= 0))
                throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("금액은 원 단위 숫자로 입력해주세요. 최대 소수점 2자리까지 가능합니다.");
        }
    }

    private static void validateYearMonth(int year, int month) {
        if (year < 2020 || year > 2100 || month < 1 || month > 12)
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "연도 또는 월이 올바르지 않습니다.");
    }

    private static String redirect(int year, int month) {
        return "redirect:/bookkeeping/monthly-view?year=" + year + "&month=" + month;
    }

    public record CategoryRow(String code, String label, BigDecimal value, BigDecimal annualTotal) {}
}
